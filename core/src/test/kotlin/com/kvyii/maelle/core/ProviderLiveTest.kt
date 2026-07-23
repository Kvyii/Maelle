package com.kvyii.maelle.core

import com.kvyii.maelle.core.http.OkHttpBackend
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory

/**
 * Live extraction tests against the real sites — the canary that tells us when
 * a provider's markup changed. Excluded by default; run with:
 *
 *   gradlew :core:test -Dmaelle.live=true                    (all providers)
 *   gradlew :core:test -Dmaelle.live=true -Dmaelle.provider=NovelBin
 *   gradlew :core:test -Dmaelle.live=true -Dmaelle.record=true   (also save fixtures)
 */
@Tag("live")
class ProviderLiveTest {

    @TestFactory
    fun `providers extract from live sites`(): List<DynamicTest> {
        val backend = OkHttpBackend()
        val client = if (System.getProperty("maelle.record") == "true") {
            RecordingHttpClient(backend, ProviderFixtureTest.fixturesRoot())
        } else {
            backend
        }
        val only = System.getProperty("maelle.provider")
        return ProviderRegistry.all(client)
            .filter { only == null || it.name.equals(only, ignoreCase = true) }
            .map { api ->
                DynamicTest.dynamicTest(api.name) {
                    runTest(timeout = kotlin.time.Duration.parse("120s")) {
                        ProviderPipeline.verifyExtraction(api)
                    }
                }
            }
    }
}
