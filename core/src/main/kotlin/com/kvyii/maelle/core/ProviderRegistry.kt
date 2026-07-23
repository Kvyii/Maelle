package com.kvyii.maelle.core

import com.kvyii.maelle.core.http.HttpClient
import com.kvyii.maelle.core.http.OkHttpBackend
import com.kvyii.maelle.core.providers.AllNovelProvider
import com.kvyii.maelle.core.providers.AnnasArchive
import com.kvyii.maelle.core.providers.BestLightNovelProvider
import com.kvyii.maelle.core.providers.FanMtlnProvider
import com.kvyii.maelle.core.providers.FreewebnovelProvider
import com.kvyii.maelle.core.providers.GraycityProvider
import com.kvyii.maelle.core.providers.HiraethTranslationProvider
import com.kvyii.maelle.core.providers.IndoWebNovelProvider
import com.kvyii.maelle.core.providers.KolNovelProvider
import com.kvyii.maelle.core.providers.LibReadProvider
import com.kvyii.maelle.core.providers.LightNovelTranslationsProvider
import com.kvyii.maelle.core.providers.MeioNovelProvider
import com.kvyii.maelle.core.providers.MoreNovelProvider
import com.kvyii.maelle.core.providers.MtlNovelProvider
import com.kvyii.maelle.core.providers.NovLoveProvider
import com.kvyii.maelle.core.providers.NovelBinProvider
import com.kvyii.maelle.core.providers.NovelFireProvider
import com.kvyii.maelle.core.providers.NovelFullProvider
import com.kvyii.maelle.core.providers.NovelsOnlineProvider
import com.kvyii.maelle.core.providers.PawReadProver
import com.kvyii.maelle.core.providers.ReadNovelFullProvider
import com.kvyii.maelle.core.providers.ReadfromnetProvider
import com.kvyii.maelle.core.providers.RoyalRoadProvider
import com.kvyii.maelle.core.providers.SakuraNovelProvider
import com.kvyii.maelle.core.providers.ScribblehubProvider
import com.kvyii.maelle.core.providers.WtrLabProvider
import com.kvyii.maelle.core.providers.WuxiaBoxProvider

/**
 * The single source of truth for available providers. Both the app and the
 * provider test suite iterate this list.
 */
object ProviderRegistry {

    fun all(client: HttpClient = OkHttpBackend()): List<MainAPI> = listOf(
        AllNovelProvider(client),
        AnnasArchive(client),
        BestLightNovelProvider(client),
        FanMtlnProvider(client),
        FreewebnovelProvider(client),
        GraycityProvider(client),
        HiraethTranslationProvider(client),
        IndoWebNovelProvider(client),
        KolNovelProvider(client),
        LibReadProvider(client),
        LightNovelTranslationsProvider(client),
        MeioNovelProvider(client),
        MoreNovelProvider(client),
        MtlNovelProvider(client),
        NovelBinProvider(client),
        NovelFireProvider(client),
        NovelFullProvider(client),
        NovelsOnlineProvider(client),
        NovLoveProvider(client),
        PawReadProver(client),
        ReadfromnetProvider(client),
        ReadNovelFullProvider(client),
        RoyalRoadProvider(client),
        SakuraNovelProvider(client),
        ScribblehubProvider(client),
        WtrLabProvider(client),
        WuxiaBoxProvider(client),
    )
}
