package com.kvyii.maelle.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kvyii.maelle.AppContainer
import com.kvyii.maelle.core.SearchResponse
import com.kvyii.maelle.data.AppTheme
import com.kvyii.maelle.data.AssistantSettings
import com.kvyii.maelle.data.Providers
import com.kvyii.maelle.data.ReaderPreferences
import com.kvyii.maelle.data.SeriesDownload
import com.kvyii.maelle.data.UpdateResult
import com.kvyii.maelle.data.db.SeriesDownloadCount
import com.kvyii.maelle.data.db.ChapterEntity
import com.kvyii.maelle.data.db.SeriesEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val library: StateFlow<List<SeriesEntity>> =
        c.library.observeLibrary()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

data class SearchUiState(
    val provider: String = Providers.all.first().name,
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchResponse> = emptyList(),
    val error: String? = null,
    /** True when [results] is the provider's popular list, not a search result. */
    val showingPopular: Boolean = false,
)

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val providerNames: List<String> = Providers.all.map { it.name }

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        loadPopular()
    }

    fun setProvider(name: String) {
        _state.value = _state.value.copy(
            provider = name, query = "", results = emptyList(), error = null,
        )
        loadPopular()
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        // Clearing the field returns to the popular list.
        if (q.isBlank() && !_state.value.showingPopular) loadPopular()
    }

    /** Load the current provider's popular/browse list (its main page). */
    fun loadPopular() {
        val provider = _state.value.provider
        loadJob?.cancel()
        if (!c.library.providerHasMainPage(provider)) {
            _state.value = _state.value.copy(
                loading = false, results = emptyList(), error = null, showingPopular = true,
            )
            return
        }
        _state.value = _state.value.copy(loading = true, error = null, showingPopular = true)
        loadJob = viewModelScope.launch {
            try {
                val results = c.library.popular(provider)
                _state.value = _state.value.copy(loading = false, results = results, showingPopular = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    fun search() {
        val s = _state.value
        if (s.query.isBlank()) {
            loadPopular()
            return
        }
        loadJob?.cancel()
        _state.value = s.copy(loading = true, error = null, showingPopular = false)
        loadJob = viewModelScope.launch {
            try {
                val results = c.library.search(s.provider, s.query)
                _state.value = _state.value.copy(loading = false, results = results, showingPopular = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Search failed")
            }
        }
    }

    /** Fetch, persist, and (optionally) add a searched series; returns its local id. */
    suspend fun open(result: SearchResponse, addToLibrary: Boolean): Long =
        c.library.openSeries(result.apiName, result.url, addToLibrary)
}

data class SeriesUiState(
    val series: SeriesEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val readCount: Int = 0,
    val refreshing: Boolean = false,
    val download: SeriesDownload? = null,
)

class SeriesViewModel(private val c: AppContainer, private val seriesId: Long) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<SeriesUiState> =
        combineSeries().stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), SeriesUiState()
        )

    private fun combineSeries(): kotlinx.coroutines.flow.Flow<SeriesUiState> {
        val seriesFlow = c.library.observeSeries(seriesId)
        val chaptersFlow = c.library.observeChapters(seriesId)
        val readFlow = c.library.observeReadCount(seriesId)
        val downloadFlow = c.downloads.downloads.map { it[seriesId] }
        return kotlinx.coroutines.flow.combine(
            seriesFlow, chaptersFlow, readFlow, refreshing, downloadFlow
        ) { series, chapters, read, isRefreshing, download ->
            SeriesUiState(series, chapters, read, isRefreshing, download)
        }
    }

    fun refresh() {
        val series = state.value.series ?: return
        refreshing.value = true
        viewModelScope.launch {
            try {
                c.library.openSeries(series.apiName, series.url, series.inLibrary)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun toggleLibrary() {
        val series = state.value.series ?: return
        viewModelScope.launch { c.library.setInLibrary(seriesId, !series.inLibrary) }
    }

    fun toggleRead(chapter: ChapterEntity) {
        viewModelScope.launch { c.library.setChapterRead(chapter.id, !chapter.isRead) }
    }

    /**
     * Long-press action: set read state for the pressed chapter and everything
     * below (older) or above (newer) it in the list — inclusive either way.
     */
    fun markRange(chapter: ChapterEntity, below: Boolean, read: Boolean) {
        viewModelScope.launch {
            c.library.markRange(seriesId, chapter.orderIndex, below, read)
        }
    }

    fun downloadChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            try {
                c.library.downloadChapter(chapter)
            } catch (_: Exception) {
                // Chapter row keeps showing the un-downloaded icon; user can retry.
            }
        }
    }

    fun downloadUnread(limit: Int? = null) = c.downloads.start(seriesId, limit)
    fun pauseDownload() = c.downloads.pause(seriesId)
    fun resumeDownload() = c.downloads.resume(seriesId)
    fun stopDownload() = c.downloads.stop(seriesId)
    fun dismissDownload() = c.downloads.clear(seriesId)
}

data class DownloadsUiState(
    val active: List<SeriesDownload> = emptyList(),
    val downloaded: List<SeriesDownloadCount> = emptyList(),
)

class DownloadsViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<DownloadsUiState> =
        kotlinx.coroutines.flow.combine(
            c.downloads.downloads,
            c.library.observeDownloadCounts(),
        ) { active, counts ->
            DownloadsUiState(
                active = active.values.sortedBy { it.seriesName },
                downloaded = counts,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsUiState())

    fun pause(seriesId: Long) = c.downloads.pause(seriesId)
    fun resume(seriesId: Long) = c.downloads.resume(seriesId)
    fun stop(seriesId: Long) = c.downloads.stop(seriesId)
    fun clear(seriesId: Long) = c.downloads.clear(seriesId)
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AssistantSettings> =
        c.settings.assistant.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantSettings()
        )

    val theme: StateFlow<AppTheme> =
        c.settings.theme.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.System
        )

    val readerPreferences: StateFlow<ReaderPreferences> =
        c.settings.readerPreferences.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences()
        )

    fun save(settings: AssistantSettings) {
        viewModelScope.launch { c.settings.update(settings) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { c.settings.setTheme(theme) }
    }

    fun savePreferences(preferences: ReaderPreferences) {
        viewModelScope.launch { c.settings.updateReaderPreferences(preferences) }
    }

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    fun checkForUpdates(currentVersion: String) {
        if (_updateState.value is UpdateCheckState.Checking) return
        _updateState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            _updateState.value = when (val result = c.updateChecker.check(currentVersion)) {
                is UpdateResult.Available -> UpdateCheckState.Available(result.version, result.url)
                UpdateResult.UpToDate -> UpdateCheckState.UpToDate
                is UpdateResult.Error -> UpdateCheckState.Error(result.message)
            }
        }
    }

    fun dismissUpdateResult() {
        _updateState.value = UpdateCheckState.Idle
    }
}

sealed interface UpdateCheckState {
    object Idle : UpdateCheckState
    object Checking : UpdateCheckState
    object UpToDate : UpdateCheckState
    data class Available(val version: String, val url: String) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

/** One chapter loaded into the continuous reader. */
data class LoadedChapter(
    val id: Long,
    val name: String,
    val body: String,
)

data class ReaderUiState(
    /** Chapters currently in the scroll, in reading order (oldest → newest). */
    val loaded: List<LoadedChapter> = emptyList(),
    val title: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val appending: Boolean = false,
    val prepending: Boolean = false,
    /** True once the final chapter of the series is loaded at the bottom. */
    val atLastChapter: Boolean = false,
    val explanation: String? = null,
    val explaining: Boolean = false,
)

class ReaderViewModel(
    private val c: AppContainer,
    private val seriesId: Long,
    private val initialChapterId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> =
        c.settings.readerPreferences.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences()
        )

    // Full ordered list (oldest → newest); [firstIndex]..[lastIndex] are loaded.
    private var chapters: List<ChapterEntity> = emptyList()
    private var firstIndex = -1
    private var lastIndex = -1

    init {
        viewModelScope.launch {
            chapters = c.library.orderedChapters(seriesId)
            val start = chapters.indexOfFirst { it.id == initialChapterId }.let { if (it < 0) 0 else it }
            firstIndex = start
            lastIndex = start
            val chapter = chapters.getOrNull(start)
            if (chapter == null) {
                _state.value = _state.value.copy(loading = false, error = "Chapter not found")
                return@launch
            }
            try {
                val body = c.library.chapterText(chapter.id)
                _state.value = _state.value.copy(
                    loaded = listOf(LoadedChapter(chapter.id, chapter.name, body)),
                    title = chapter.name,
                    loading = false,
                    atLastChapter = lastIndex >= chapters.lastIndex,
                )
                c.library.setLastReadChapter(seriesId, chapter.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    /** Append the next chapter to the bottom of the scroll (call when near the end). */
    fun loadNext() {
        if (_state.value.appending || lastIndex >= chapters.lastIndex || lastIndex < 0) return
        _state.value = _state.value.copy(appending = true)
        viewModelScope.launch {
            val next = chapters[lastIndex + 1]
            val body = runCatching { c.library.chapterText(next.id) }.getOrNull()
            if (body != null) {
                lastIndex++
                _state.value = _state.value.copy(
                    loaded = _state.value.loaded + LoadedChapter(next.id, next.name, body),
                    appending = false,
                    atLastChapter = lastIndex >= chapters.lastIndex,
                )
            } else {
                _state.value = _state.value.copy(appending = false)
            }
        }
    }

    /** Prepend the previous chapter to the top of the scroll (call when near the top). */
    fun loadPrevious() {
        if (_state.value.prepending || firstIndex <= 0) return
        _state.value = _state.value.copy(prepending = true)
        viewModelScope.launch {
            val prev = chapters[firstIndex - 1]
            val body = runCatching { c.library.chapterText(prev.id) }.getOrNull()
            if (body != null) {
                firstIndex--
                _state.value = _state.value.copy(
                    loaded = listOf(LoadedChapter(prev.id, prev.name, body)) + _state.value.loaded,
                    prepending = false,
                )
            } else {
                _state.value = _state.value.copy(prepending = false)
            }
        }
    }

    /** The chapter whose heading is currently at the top of the viewport. */
    fun onChapterVisible(chapterId: Long) {
        val chapter = _state.value.loaded.firstOrNull { it.id == chapterId } ?: return
        if (_state.value.title != chapter.name) {
            _state.value = _state.value.copy(title = chapter.name)
        }
        // Track reading position, but do NOT mark read yet — that happens only
        // once the reader scrolls to the bottom of the chapter.
        viewModelScope.launch { c.library.setLastReadChapter(seriesId, chapterId) }
    }

    /** Called when the reader has scrolled to the end of a chapter's body. */
    fun onChapterFinished(chapterId: Long) {
        viewModelScope.launch { c.library.setChapterRead(chapterId, true) }
    }

    fun explain(selected: String, paragraph: String) {
        _state.value = _state.value.copy(explaining = true, explanation = null)
        viewModelScope.launch {
            try {
                val settings = c.settings.assistant.first()
                if (!settings.isConfigured) {
                    _state.value = _state.value.copy(
                        explaining = false,
                        explanation = "Set your OpenRouter API key in Settings first."
                    )
                    return@launch
                }
                val result = c.assistant.explain(selected, paragraph, settings)
                _state.value = _state.value.copy(explaining = false, explanation = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    explaining = false,
                    explanation = "Error: ${e.message}"
                )
            }
        }
    }

    fun dismissExplanation() {
        _state.value = _state.value.copy(explanation = null)
    }
}

/** Factory that hands each ViewModel the app container (and any route args). */
class MaelleViewModelFactory(
    private val c: AppContainer,
    private val args: Map<String, Long> = emptyMap(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(c) as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(c) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(c) as T
        modelClass.isAssignableFrom(DownloadsViewModel::class.java) -> DownloadsViewModel(c) as T
        modelClass.isAssignableFrom(SeriesViewModel::class.java) ->
            SeriesViewModel(c, args.getValue("seriesId")) as T
        modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
            ReaderViewModel(c, args.getValue("seriesId"), args.getValue("chapterId")) as T
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
