package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient


class FanMtlnProvider(client: HttpClient) : WuxiaBoxProvider(client)
{
    override val name = "FanMTL"
    override val mainUrl = "https://www.fanmtl.com"
    override val hasMainPage = true
}