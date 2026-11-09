# Publishing `offline-auth-android`

This document describes how to prepare, validate, sign, and publish `offline-auth-android` to Maven Central.

## Maven Coordinates

```text
groupId:    io.github.john-dembaremba
artifactId: offline-auth-android
version:    0.1.0
```

The Maven Central namespace is:

```text
io.github.john-dembaremba
```

It is verified through the GitHub account associated with the Central Portal.

The Android/Kotlin package namespace remains:

```text
io.github.johndembaremba.offlineauth
```

The Maven `groupId` and Kotlin package name do not need to be identical.

## Local Gradle Configuration

Repository-level `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

User-specific Gradle configuration is stored outside the repository:

```text
~/.gradle/gradle.properties
```

Example:

```properties
signing.gnupg.keyName=54E3ABA77FA1****
```

Do not commit signing credentials, Maven Central credentials, private keys, or passphrases.

## GPG Signing Key

Current signing key:

```text
Key ID:
54E3ABA77FA1****

Fingerprint:
3344CF7326485CDE29C236DC54E3ABA77FA1****
```

List the local key:

```bash
gpg --list-secret-keys --keyid-format LONG
```

Verify that the public key is available from the keyserver:

```bash
gpg --keyserver keyserver.ubuntu.com \
  --search-keys 54E3ABA77FA1****
```

The public key must remain discoverable so Maven Central can verify artifact signatures.

Never publish or commit the private key.

## Pre-Release Validation

Before publishing, run:

```bash
make format
make lint
make test
make android-test
```

Build the release AAR:

```bash
./gradlew :offlineauth:assembleRelease
```

Build the consumer sample:

```bash
./gradlew :sample-app:assembleDebug
```

For final release validation:

```bash
make android-full-test
```

## Generate Maven Artifacts Locally

Publish the release to the local Maven repository:

```bash
./gradlew :offlineauth:publishReleasePublicationToMavenLocal
```

Artifacts are generated under:

```text
~/.m2/repository/io/github/john-dembaremba/offline-auth-android/<version>/
```

Expected files include:

```text
offline-auth-android-<version>.aar
offline-auth-android-<version>.aar.asc

offline-auth-android-<version>.pom
offline-auth-android-<version>.pom.asc

offline-auth-android-<version>-sources.jar
offline-auth-android-<version>-sources.jar.asc

offline-auth-android-<version>-javadoc.jar
offline-auth-android-<version>-javadoc.jar.asc

offline-auth-android-<version>.module
offline-auth-android-<version>.module.asc
```

## Verify the Generated POM

Inspect:

```bash
cat ~/.m2/repository/io/github/john-dembaremba/offline-auth-android/<version>/offline-auth-android-<version>.pom
```

Confirm:

```text
groupId     io.github.john-dembaremba
artifactId  offline-auth-android
version     expected release version
packaging   aar
license     MIT
developer   John Dembaremba
SCM         correct GitHub repository
```

Also verify dependency scopes.

Current expected dependencies:

```text
kotlinx-serialization-json   compile
kotlin-stdlib                compile
kotlinx-coroutines-core      runtime
```

## Verify Artifact Signature

Example:

```bash
gpg --verify \
  ~/.m2/repository/io/github/john-dembaremba/offline-auth-android/<version>/offline-auth-android-<version>.aar.asc \
  ~/.m2/repository/io/github/john-dembaremba/offline-auth-android/<version>/offline-auth-android-<version>.aar
```

Expected result:

```text
Good signature from "John Dembaremba ..."
```

## Maven Central Requirements

The publication must include:

- primary AAR
- POM
- sources JAR
- Javadoc JAR
- PGP signatures
- license metadata
- developer metadata
- SCM information
- valid Maven coordinates

The verified Central namespace must match the published `groupId`.

Current verified namespace:

```text
io.github.john-dembaremba
```

## Central Portal User Token

Generate a user token from the Maven Central Portal account page.

Do not use the normal Central login password for automated publishing.

Store Central credentials outside the repository, for example in:

```text
~/.gradle/gradle.properties
```

Never commit the generated username/token password.

## Central Portal Publishing

Maven Central currently uses the Central Publisher Portal.

For Gradle projects, there is currently no official Central Portal Gradle plugin. The project may therefore use the Portal Publisher API or another supported publishing integration.

The current publishing workflow should be verified against the official Sonatype documentation before each release because the Portal publishing process may evolve.

## Recommended Release Flow

1. Update the library version.
2. Update changelog/release notes.
3. Run formatting and linting.
4. Run JVM tests.
5. Run Android compatibility tests.
6. Run the full Android release test suite.
7. Build the release AAR.
8. Build the consumer sample app.
9. Publish to Maven Local.
10. Inspect the generated POM.
11. Verify signed artifacts.
12. Confirm the public GPG key is discoverable.
13. Upload the signed Maven bundle to Central Portal.
14. Wait for Central validation.
15. Review validation results.
16. Publish the validated deployment.
17. Verify the artifact appears on Maven Central.
18. Create the Git tag and GitHub release.

## Important Release Rule

Maven Central releases are immutable.

Once a version is successfully published, do not attempt to replace or modify that version.

If a release contains an error, publish a new version instead.

For example:

```text
0.1.0
0.1.1
0.2.0
```

## Consumer Installation

After publication, consumers will use:

```kotlin
dependencies {
    implementation(
        "io.github.john-dembaremba:offline-auth-android:<version>",
    )
}
```

Consumers must also enable core library desugaring:

```kotlin
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5",
    )
}
```

## References

Before publishing a release, verify the current Maven Central requirements and Gradle publishing guidance in the official Sonatype Central documentation.
