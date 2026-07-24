package com.kvyii.maelle

import android.app.Application
import com.kvyii.maelle.assistant.OpenRouterClient
import com.kvyii.maelle.data.DownloadManager
import com.kvyii.maelle.data.LibraryRepository
import com.kvyii.maelle.data.SettingsRepository
import com.kvyii.maelle.data.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MaelleApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Minimal manual DI — a rewrite this size doesn't need Hilt. */
class AppContainer(app: MaelleApplication) {
    /** App-lifetime scope for work that must outlive a screen (downloads, progress saves). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = SettingsRepository(app)
    val library = LibraryRepository(app, settings)
    val downloads = DownloadManager(app, library, appScope)
    val assistant = OpenRouterClient()
    val updateChecker = UpdateChecker()
}
