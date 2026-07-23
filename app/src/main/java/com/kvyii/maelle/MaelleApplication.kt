package com.kvyii.maelle

import android.app.Application
import com.kvyii.maelle.assistant.OpenRouterClient
import com.kvyii.maelle.data.LibraryRepository
import com.kvyii.maelle.data.SettingsRepository

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
    val settings = SettingsRepository(app)
    val library = LibraryRepository(app, settings)
    val assistant = OpenRouterClient()
}
