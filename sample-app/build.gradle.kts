plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.johndembaremba.offlineauth.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.johndembaremba.offlineauth.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(files("../offlineauth/build/outputs/aar/offlineauth-release.aar"))

    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5",
    )
}
