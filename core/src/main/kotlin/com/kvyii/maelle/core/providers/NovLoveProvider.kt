package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient

class NovLoveProvider(client: HttpClient) : NovelBinProvider(client)
{
    override val name = "NovLove"
    override val mainUrl = "https://novlove.com"
    override val hasMainPage = false
}