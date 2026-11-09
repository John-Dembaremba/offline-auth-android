# Publishing `offline-auth-android`

This document describes how to prepare, validate, sign, and publish `offline-auth-android` to Maven Central.

## Maven Coordinates

```text
groupId:    io.github.john-dembaremba
artifactId: offline-auth-android
version:    <release-version>
```

Current published release:

```text
io.github.john-dembaremba:offline-auth-android:0.1.0
```

The verified Maven Central namespace is:

```text
io.github.john-dembaremba
```

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

Signing configuration is stored outside the repository in:

```text
~/.gradle/gradle.properties
```

Example:

```properties
signing.gnupg.keyName=54E3ABA77FA1****
```

Never commit:

- private keys
- signing passphrases
- Maven Central tokens
- publishing credentials

## GPG Signing

List the local signing key:

```bash
gpg --list-secret-keys --keyid-format LONG
```

Verify that its public key is discoverable:

```bash
gpg --keyserver keyserver.ubuntu.com \
  --search-keys 54E3ABA77FA1****
```

The public key must remain available so Maven Central can verify artifact signatures.

Never publish the private key.

## Pre-Release Validation

Run:

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

For final release qualification:

```bash
make android-full-test
```

## Publish to Maven Local

Generate the release artifacts locally:

```bash
./gradlew :offlineauth:publishReleasePublicationToMavenLocal
```

Artifacts are written to:

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

## Verify the POM

Inspect:

```bash
cat ~/.m2/repository/io/github/john-dembaremba/offline-auth-android/<version>/offline-auth-android-<version>.pom
```

Confirm:

```text
groupId     io.github.john-dembaremba
artifactId  offline-auth-android
version     expected version
packaging   aar
license     MIT License
developer   John Dembaremba
SCM         correct GitHub repository
```

Expected dependency scopes:

```text
kotlinx-serialization-json   compile
kotlin-stdlib                compile
kotlinx-coroutines-core      runtime
```

## Verify Artifact Signing

Example:

```bash
gpg --verify \
  ~/.m2/repository/io/github/john-dembaremba/offline-auth-android/<version>/offline-auth-android-<version>.aar.asc \
  ~/.m2/repository/io/github/john-dembaremba/offline-auth-android/<version>/offline-auth-android-<version>.aar
```

Expected:

```text
Good signature from "John Dembaremba ..."
```

## Create the Maven Central Bundle

Create a temporary Maven-layout directory:

```bash
VERSION=0.1.0

rm -rf /tmp/offline-auth-central

mkdir -p \
  /tmp/offline-auth-central/io/github/john-dembaremba/offline-auth-android/$VERSION
```

Copy the locally published artifacts:

```bash
cp ~/.m2/repository/io/github/john-dembaremba/offline-auth-android/$VERSION/* \
  /tmp/offline-auth-central/io/github/john-dembaremba/offline-auth-android/$VERSION/
```

Generate MD5 and SHA1 checksums:

```bash
cd /tmp/offline-auth-central/io/github/john-dembaremba/offline-auth-android/$VERSION

for file in \
  offline-auth-android-$VERSION.aar \
  offline-auth-android-$VERSION.pom \
  offline-auth-android-$VERSION-sources.jar \
  offline-auth-android-$VERSION-javadoc.jar \
  offline-auth-android-$VERSION.module
do
    md5sum "$file" | awk '{print $1}' > "$file.md5"
    sha1sum "$file" | awk '{print $1}' > "$file.sha1"
done
```

Create the upload ZIP:

```bash
cd /tmp/offline-auth-central

zip -r \
  ~/projects/offline-auth-android/offline-auth-android-$VERSION-central.zip \
  io
```

Verify its structure:

```bash
unzip -l \
  ~/projects/offline-auth-android/offline-auth-android-$VERSION-central.zip
```

The archive must contain the Maven directory hierarchy:

```text
io/
└── github/
    └── john-dembaremba/
        └── offline-auth-android/
            └── <version>/
```

## Upload to Maven Central

Open the Maven Central Portal and go to:

```text
Publishing Settings → Deployments → Publish Component
```

Use:

```text
Deployment Name:
io.github.john-dembaremba:offline-auth-android:<version>
```

Example description:

```text
Offline Auth Android <version> release
```

Upload:

```text
offline-auth-android-<version>-central.zip
```

Central will process and validate the component.

Review the deployment before final publication.

## Published Releases

The first Maven Central release was successfully published as:

```text
io.github.john-dembaremba:offline-auth-android:0.1.0
```

Status:

```text
PUBLISHED
```

## Important Release Rule

Maven Central releases are immutable.

Once a version is published, it cannot be replaced or modified.

If a released version contains a problem, publish a new version:

```text
0.1.0
0.1.1
0.2.0
```

Never reuse an already published version.

## Recommended Release Flow

1. Update the library version.
2. Update release notes/changelog.
3. Run formatting and linting.
4. Run JVM tests.
5. Run Android compatibility tests.
6. Run the full Android release suite.
7. Build the release AAR.
8. Build the consumer sample app.
9. Publish to Maven Local.
10. Inspect the generated POM.
11. Verify artifact signatures.
12. Confirm the public GPG key is discoverable.
13. Create the Maven Central bundle.
14. Generate checksums.
15. Inspect the ZIP layout.
16. Upload through Central Portal.
17. Review Central validation.
18. Publish the deployment.
19. Verify the artifact in Maven Central.
20. Create the Git tag.
21. Create the GitHub release.

## Consumer Installation

Consumers can install the library with:

```kotlin
dependencies {
    implementation(
        "io.github.john-dembaremba:offline-auth-android:0.1.0",
    )
}
```

Core library desugaring must also be enabled:

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

Before future releases, verify the latest Maven Central publishing requirements because the Central Portal workflow may change.
