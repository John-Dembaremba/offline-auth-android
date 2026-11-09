# Testing

`offline-auth-android` uses JVM unit tests and Android instrumentation tests to verify parsing, cryptography, synchronization, persistence, authentication, security behavior, Android compatibility, and performance.

Instrumentation tests run against a real Android runtime and Android Keystore.

## Test Coverage

The suite covers:

- credential parsing and validation
- PBKDF2-SHA256 password verification
- local authentication
- encrypted envelope validation and processing
- authoritative snapshot synchronization
- credential-version rollback protection
- encrypted credential persistence
- Android Keystore behavior
- malformed payload and unsupported-algorithm handling
- unsupported protocol versions
- memory and parallelism behavior
- large snapshots and repeated synchronization
- mixed success/failure workloads

## JVM Tests

Run:

```bash
make test
```

This executes:

```bash
./gradlew :offlineauth:testDebugUnitTest
```

JVM tests cover logic that does not require Android framework or Keystore access.

## Instrumentation Compilation

Compile Android tests without running an emulator:

```bash
make android-test-compile
```

## Standard Android Tests

Run:

```bash
make android-test
```

This is the normal compatibility and security suite.

It covers:

- end-to-end synchronization
- Android Keystore
- RSA-OAEP-SHA256
- AES-256-GCM
- encrypted storage
- authentication
- PBKDF2 verification
- rollback protection
- malformed payloads
- unsupported algorithms
- protocol validation

Heavy benchmark and stress tests are intentionally excluded.

## Android Compatibility

The current compatibility matrix is:

| Android version | API | Status | Notes |
|---|---:|---|---|
| Android 6.0 | 23 | ✅ Passed | Minimum supported API; PBKDF2 fallback validated |
| Android 9 | 28 | ✅ Passed | Native PBKDF2 path validated |
| Android 13 | 33 | ✅ Passed | Standard compatibility suite validated |
| Android 17 test image | 37 | ✅ Passed | Primary benchmark environment |

API 23–25 use a PBKDF2-HMAC-SHA256 fallback implemented with `HmacSHA256`.

API 26+ use:

```text
SecretKeyFactory("PBKDF2WithHmacSHA256")
```

Both paths preserve the same Django-compatible PBKDF2-SHA256 verifier format.

The fallback is validated using known Django-compatible vectors, and newer Android versions also verify that the native and fallback implementations derive the same key.

On API 23, the native comparison test is intentionally skipped because the native provider does not exist.

## End-to-End Integration

`OfflineAuthIntegrationTest` verifies the complete flow:

1. create `OfflineAuthManager`
2. retrieve the RSA public key
3. create a PBKDF2 verifier
4. build a credential payload
5. encrypt with AES-256-GCM
6. wrap the AES key using RSA-OAEP-SHA256
7. build and synchronize a protocol-version-1 snapshot
8. authenticate with the correct password
9. reject an incorrect password

## Snapshot and Rollback Semantics

Snapshots are authoritative.

The tests verify that:

- successful credentials replace existing credentials
- backend failures preserve existing credentials
- local-processing failures preserve existing credentials
- users absent from the snapshot are removed
- same credential version may replace an existing credential
- higher versions are accepted
- lower versions are rejected
- rollback failures preserve the higher stored version

## Security Tests

Security instrumentation covers:

- malformed Base64
- invalid AES-GCM nonce
- wrong RSA wrapping key
- corrupted ciphertext
- invalid decrypted JSON
- expired credentials
- unsupported password algorithms
- unsupported key algorithms
- unsupported content algorithms
- unsupported protocol versions

Android Keystore tests also verify that private RSA key material is not exportable:

```text
privateKey.encoded == null
privateKey.format == null
```

Storage-security tests verify that the following are not persisted in plaintext:

- user password
- PBKDF2 verifier
- decrypted credential JSON

## Mixed-Failure Testing

The mixed-failure test processes 1000 users:

```text
900 valid
50 backend failures
30 corrupted ciphertext payloads
20 rollback attempts
```

Expected result:

```text
success=900
backendFailures=50
decryptionFailures=30
rollbackFailures=20
totalFailures=100
```

The test verifies that valid users remain usable while existing credentials are preserved where required.

## Performance and Stress Tests

Run individual heavy suites:

```bash
make android-performance-test
make android-parallelism-test
make android-memory-test
make android-stress-test
make android-mixed-failure-test
```

Run all heavy tests:

```bash
make android-heavy-test
```

Run every instrumentation test:

```bash
make android-full-test
```

The full suite is intended mainly for release validation.

Current stress workloads:

```text
100 users × 100 cycles
500 users × 50 cycles
1000 users × 25 cycles
```

This represents approximately 60,000 credential synchronization operations.

See `docs/PERFORMANCE.md` for detailed benchmark results.

## Known Android Test Tooling Warning

API 23 instrumentation may print:

```text
Failed to retrieve additional test outputs from device.
java.io.IOException: File name too long
```

This originates from Android tooling's `AndroidAdditionalTestOutputCollector`.

Observed runs still complete successfully with:

```text
BUILD SUCCESSFUL
```

This is currently treated as a test-output collection issue rather than a library failure.

## Build and Quality Commands

Run formatting:

```bash
make format
```

Check formatting:

```bash
make format-check
```

Run linting:

```bash
make lint
```

Run the standard project validation:

```bash
make
```

The default target runs:

```text
format
lint
JVM tests
Android instrumentation compilation
debug library build
```

It does not run emulator-dependent instrumentation tests.

## Recommended Workflow

During development:

```bash
make
make android-test
```

When changing cryptography, synchronization, persistence, or concurrency:

```bash
make
make android-test
make android-performance-test
make android-memory-test
```

Before release:

```bash
make
make precommit
make android-full-test
```

## Release Expectations

A release candidate should have:

- formatting and linting passing
- JVM tests passing
- instrumentation tests compiling
- standard Android tests passing
- compatibility validated across the supported API matrix
- security and storage-security tests passing
- end-to-end synchronization passing
- full instrumentation suite passing
- no significant performance regression
- no obvious unbounded memory growth
- successful library build

Performance and memory results should always be interpreted relative to the documented benchmark environment.
