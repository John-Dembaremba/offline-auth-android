plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.johndembaremba.offlineauth"

    compileSdk = 36

    defaultConfig {
        minSdk = 23

        consumerProguardFiles(
            "consumer-rules.pro",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
