plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.johndembaremba.offlineauth"

    compileSdk = 36

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles(
            "consumer-rules.pro",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21

        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5",
    )

    api(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0",
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
    )

    testImplementation(
        "junit:junit:4.13.2",
    )

    androidTestImplementation(
        "androidx.test.ext:junit:1.3.0",
    )

    androidTestImplementation(
        "androidx.test:runner:1.7.0",
    )
}
