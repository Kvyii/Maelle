package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient


class IndoWebNovelProvider(client: HttpClient) : WPReader(client) {
    override val name = "IndoWebNovel"
    override val mainUrl = "https://indowebnovel.id"
}
