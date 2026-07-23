plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Ported providers use @JsonProperty on constructor params; opt in to the
        // future param+property target to silence the KT-73255 deprecation noise.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    api(libs.jsoup)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.jackson.kotlin)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        // Live network tests are opt-in: gradlew :core:test -Dmaelle.live=true
        if (System.getProperty("maelle.live") != "true") {
            excludeTags("live")
        }
    }
    // Forward flags controlling live/record modes to the test JVM
    listOf("maelle.live", "maelle.record", "maelle.provider").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
