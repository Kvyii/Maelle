package com.kvyii.maelle.data

import com.kvyii.maelle.core.MainAPI
import com.kvyii.maelle.core.ProviderRegistry
import com.kvyii.maelle.core.http.OkHttpBackend

/** App-wide provider registry, sharing a single OkHttp backend. */
object Providers {
    private val backend = OkHttpBackend()

    val all: List<MainAPI> by lazy { ProviderRegistry.all(backend) }

    fun byName(name: String): MainAPI? = all.firstOrNull { it.name == name }
}
