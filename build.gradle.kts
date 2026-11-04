plugins {
    id("com.android.library") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.diffplug.spotless") version "8.11.0"
}

spotless {
    kotlin {
        target("**/*.kt")
        ktlint()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
    }
}
