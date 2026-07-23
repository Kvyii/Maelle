package com.kvyii.maelle.core

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Offline extraction tests replaying recorded HTML fixtures. Deterministic and
 * CI-safe. Providers without recorded fixtures are skipped (record them with
 * `gradlew :core:test -Dmaelle.live=true -Dmaelle.record=true`).
 */
class ProviderFixtureTest {

    private val fixtureDir: Path = fixturesRoot()

    @TestFactory
    fun `providers extract from recorded fixtures`(): List<DynamicTest> {
        val client = FixtureHttpClient(fixtureDir)
        val only = System.getProperty("maelle.provider")
        return ProviderRegistry.all(client)
            .filter { only == null || it.name.equals(only, ignoreCase = true) }
            .map { api ->
                DynamicTest.dynamicTest(api.name) {
                    runTest {
                        try {
                            ProviderPipeline.verifyExtraction(api)
                        } catch (e: FixtureMissingException) {
                            assumeTrue(false, e.message)
                        }
                    }
                }
            }
    }

    companion object {
        fun fixturesRoot(): Path {
            // Tests run with the module directory as the working dir under Gradle.
            val candidates = listOf(
                Path.of("src", "test", "resources", "fixtures"),
                Path.of("core", "src", "test", "resources", "fixtures"),
            )
            return candidates.firstOrNull { Files.exists(it) } ?: candidates.first()
        }
    }
}
