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
)

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val providerNames: List<String> = Providers.all.map { it.name }

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun setProvider(name: String) {
        _state.value = _state.value.copy(provider = name, results = emptyList(), error = null)
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() {
        val s = _state.value
        if (s.query.isBlank()) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val results = c.library.search(s.provider, s.query)
                _state.value = _state.value.copy(loading = false, results = results)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Search failed")
            }
        }
    }

    /** Fetch, persist, and (optionally) add a searched series; returns its local id. */
    suspend fun open(result: SearchResponse, addToLibrary: Boolean): Long =
        c.library.openSeries(result.apiName, result.url, addToLibrary)
}

data class DownloadProgress(val done: Int, val total: Int, val failed: List<String> = emptyList()) {
    val finished: Boolean get() = done >= total
}

data class SeriesUiState(
    val series: SeriesEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val readCount: Int = 0,
    val refreshing: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
)

class SeriesViewModel(private val c: AppContainer, private val seriesId: Long) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val downloadProgress = MutableStateFlow<DownloadProgress?>(null)

    val state: StateFlow<SeriesUiState> =
        combineSeries().stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), SeriesUiState()
        )

    private fun combineSeries(): kotlinx.coroutines.flow.Flow<SeriesUiState> {
        val seriesFlow = c.library.observeSeries(seriesId)
        val chaptersFlow = c.library.observeChapters(seriesId)
        val readFlow = c.library.observeReadCount(seriesId)
        return kotlinx.coroutines.flow.combine(
            seriesFlow, chaptersFlow, readFlow, refreshing, downloadProgress
        ) { series, chapters, read, isRefreshing, progress ->
            SeriesUiState(series, chapters, read, isRefreshing, progress)
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

    /** Download every unread chapter with per-chapter retry, reporting progress. */
    fun downloadAllUnread() {
        if (downloadProgress.value?.finished == false) return // already running
        viewModelScope.launch {
            downloadProgress.value = DownloadProgress(0, Int.MAX_VALUE)
            val result = c.library.downloadAllUnread(seriesId) { done, total ->
                downloadProgress.value = DownloadProgress(done, total)
            }
            downloadProgress.value =
                DownloadProgress(result.total, result.total, result.failed)
        }
    }

    fun dismissDownloadProgress() {
        downloadProgress.value = null
    }
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
}

data class ReaderUiState(
    val chapterName: String = "",
    val html: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val explanation: String? = null,
    val explaining: Boolean = false,
)

class ReaderViewModel(
    private val c: AppContainer,
    private val seriesId: Long,
    private val chapterId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> =
        c.settings.readerPreferences.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences()
        )

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val text = c.library.chapterText(chapterId)
                _state.value = _state.value.copy(html = text, loading = false)
                c.library.setChapterRead(chapterId, true)
                c.library.setLastReadChapter(seriesId, chapterId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load")
            }
        }
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
        modelClass.isAssignableFrom(SeriesViewModel::class.java) ->
            SeriesViewModel(c, args.getValue("seriesId")) as T
        modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
            ReaderViewModel(c, args.getValue("seriesId"), args.getValue("chapterId")) as T
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
