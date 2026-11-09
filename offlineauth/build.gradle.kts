plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
    id("signing")
}

group = "io.github.john-dembaremba"
version = "0.1.0"

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

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
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

publishing {
    publications {
        register<MavenPublication>("release") {
            group = "io.github.john-dembaremba"
            artifactId = "offline-auth-android"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Offline Auth Android")

                description.set(
                    "Secure offline authentication for Android with Android Keystore, encrypted credential storage, and Django-compatible PBKDF2 verification.",
                )

                url.set(
                    "https://github.com/John-dembaremba/offline-auth-android",
                )

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("john-dembaremba")
                        name.set("John Dembaremba")
                    }
                }

                scm {
                    connection.set(
                        "scm:git:https://github.com/John-dembaremba/offline-auth-android.git",
                    )

                    developerConnection.set(
                        "scm:git:ssh://git@github.com/John-dembaremba/offline-auth-android.git",
                    )

                    url.set(
                        "https://github.com/John-dembaremba/offline-auth-android",
                    )
                }
            }
        }
    }
}

signing {
    useGpgCmd()

    sign(
        publishing.publications["release"],
    )
}
