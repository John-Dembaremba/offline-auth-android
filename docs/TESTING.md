# Testing

`offline-auth-android` uses JVM unit tests and Android instrumentation tests to verify credential parsing, cryptography, synchronization, persistence, authentication, security behavior, and performance.

The Android instrumentation suite exercises the library using a real Android runtime and Android Keystore.

## Test Categories

The test suite covers:

- credential parsing
- credential validation
- PBKDF2 password verification
- local authentication
- envelope validation
- encrypted envelope processing
- snapshot parsing
- synchronization
- authoritative snapshot semantics
- credential-version rollback protection
- encrypted credential persistence
- Android Keystore behavior
- end-to-end synchronization and authentication
- malformed encrypted payloads
- security failure handling
- unsupported algorithms
- unsupported protocol versions
- memory behavior
- concurrency and parallelism
- repeated synchronization
- large snapshot handling
- mixed success/failure workloads

## JVM Unit Tests

Run:

```bash
make test
```

This executes:

```bash
./gradlew :offlineauth:testDebugUnitTest
```

JVM tests are used where Android framework or Android Keystore access is not required.

Examples include:

- credential parsing
- validators
- PBKDF2 verification
- authentication logic
- snapshot parsing
- envelope validation
- synchronization rules
- adaptive parallelism

## Instrumentation Test Compilation

Instrumentation tests can be compiled without requiring a running Android emulator:

```bash
make android-test-compile
```

This runs:

```bash
./gradlew :offlineauth:compileDebugAndroidTestKotlin
```

This target is included in the normal project validation flow.

## Standard Android Tests

Run:

```bash
make android-test
```

The normal instrumentation command intentionally excludes the expensive performance and soak suites.

It exercises functional and security-sensitive Android behavior without requiring the full benchmark workload.

## Full Android Test Suite

Run:

```bash
make android-full-test
```

This executes every instrumentation test, including:

- performance benchmarks
- parallelism benchmarks
- memory measurements
- repeated stress tests
- mixed-failure stress testing

The full suite is intended primarily for release validation.

A complete observed run executed 44 instrumentation tests, including integration, security, storage-security, performance, and stress tests. Security-storage checks completed without failures.

## End-to-End Integration

`OfflineAuthIntegrationTest` verifies the complete Android workflow.

The test:

1. Creates `OfflineAuthManager`.
2. Retrieves its RSA public key.
3. Generates a PBKDF2-SHA256 verifier.
4. Creates a credential payload.
5. Generates a random AES-256 key.
6. Encrypts the credential using AES-256-GCM.
7. Wraps the AES key using RSA-OAEP-SHA256.
8. Builds a protocol version 1 snapshot.
9. Parses the snapshot.
10. Synchronizes it.
11. Authenticates using the correct password.
12. Rejects an incorrect password.

This validates interoperability between the expected backend envelope format and the Android implementation.

## Authoritative Snapshot Semantics

Snapshots are authoritative.

For a synchronized snapshot:

- successfully processed credentials replace existing credentials
- users reported as backend failures preserve an existing local credential
- credentials that fail local processing preserve an existing credential when one exists
- users absent from the authoritative snapshot are removed

Tests explicitly verify these rules.

## Credential Version Protection

Credential versions prevent rollback of offline authentication state.

Rules tested include:

- same credential version may replace the existing credential
- higher credential version is accepted
- lower credential version is rejected
- rejected rollback attempts preserve the existing higher-version credential

Rollback failure information includes:

- stored credential version
- incoming credential version

## Mixed-Failure Testing

The mixed-failure stress test processes a 1000-user authoritative snapshot containing:

- 900 valid credentials
- 50 backend failures
- 30 corrupted ciphertext payloads
- 20 credential rollback attempts

Expected and observed result:

```text
success=900
backendFailures=50
decryptionFailures=30
rollbackFailures=20
totalFailures=100
```

The test also verifies that:

- valid users remain authenticatable
- backend-failure users retain previously stored credentials
- corrupted users without an existing credential remain unavailable offline
- rollback attempts preserve the previously stored higher credential version

The observed instrumentation run completed this test successfully.

## Security Failure Tests

`OfflineAuthSecurityFailureTest` verifies defensive handling of malformed or unsupported input.

Covered scenarios include:

### Malformed Base64

Malformed cryptographic data must not crash the library.

It is converted into a controlled local decryption failure.

### Invalid AES-GCM Nonce

An invalid nonce is rejected and reported as a decryption failure.

### Wrong RSA Wrapping Key

An AES key wrapped using an unrelated RSA key cannot be decrypted using the application's Android Keystore private key.

The envelope is rejected.

### Corrupted Ciphertext

Modification of encrypted AES-GCM ciphertext causes authentication failure and the credential is rejected.

### Invalid Credential JSON

A successfully decrypted payload that does not contain valid credential JSON is rejected as an invalid credential payload.

### Expired Credentials

Credentials whose expiration time is in the past are rejected during validation.

### Unsupported Password Algorithm

Password algorithms other than supported protocol algorithms are rejected.

### Unsupported Key Algorithm

Unknown key-wrapping algorithms are rejected before cryptographic processing.

### Unsupported Content Algorithm

Unknown content-encryption algorithms are rejected before decryption.

The dedicated security suite executed all nine security failure tests successfully in the observed run.

## Android Keystore Security

Instrumentation tests verify that the RSA private key remains controlled by Android Keystore.

The test locates the RSA key corresponding to the public key exported by the library and verifies that:

```text
privateKey.encoded == null
privateKey.format == null
```

The application can use the key for cryptographic operations but cannot export the private key material.

## Plaintext Persistence

Offline credentials must not be persisted in plaintext.

The storage-security instrumentation test synchronizes a credential and scans application-private files for:

- the original password
- the PBKDF2 verifier
- the complete decrypted credential JSON

None of these values should appear directly in application-private persistent storage.

The observed storage-security test completed successfully.

## Protocol Version Protection

Unsupported protocol versions are rejected before they can replace valid stored credentials.

The test first stores a valid protocol version 1 credential and then attempts to synchronize a replacement using an unsupported protocol version.

It verifies that:

- `UnsupportedProtocolVersionException` is raised
- the original credential remains usable
- the replacement password is not accepted

This prevents an unsupported snapshot format from partially mutating existing offline authentication state.

## Storage Testing

Encrypted credential storage tests verify:

- storing credentials
- reading credentials
- replacing credentials
- removing credentials
- authoritative snapshot application
- preservation of credentials on failed processing
- encrypted persistence behavior

Credentials are encrypted at rest using an Android Keystore-backed AES key.

## Performance Testing

Performance tests are intentionally separated from the standard Android test command.

Run:

```bash
make android-performance-test
```

See:

```text
docs/PERFORMANCE.md
```

for measured results.

## Parallelism Testing

Run:

```bash
make android-parallelism-test
```

The test compares fixed parallelism levels:

```text
1
2
4
```

The current benchmark evidence supports the adaptive strategy capped at four concurrent credential processors.

## Memory Testing

Run:

```bash
make android-memory-test
```

Memory measurements are taken before synchronization, after synchronization, and after garbage collection.

They are intended to detect obvious regressions or unbounded accumulation rather than serve as formal heap-leak proof.

## Stress Testing

Run:

```bash
make android-stress-test
```

Current stress workloads include:

```text
100 users × 100 cycles
500 users × 50 cycles
1000 users × 25 cycles
```

This represents approximately 60,000 credential synchronization operations.

The test checks:

- successful synchronization
- repeated replacement
- memory behavior
- stability
- absence of correctness failures during sustained processing

## Mixed-Failure Stress Test

Run:

```bash
make android-mixed-failure-test
```

This verifies that a large authoritative snapshot remains correctly processable even when multiple failure classes occur concurrently.

## Formatting

Apply formatting:

```bash
make format
```

Check formatting without modifying files:

```bash
make format-check
```

## Linting

Run:

```bash
make lint
```

This executes:

```bash
./gradlew :offlineauth:lintDebug
```

## Build Validation

Run:

```bash
make
```

The default target performs:

```text
format
lint
JVM tests
Android instrumentation test compilation
debug library build
```

It deliberately does not execute emulator-dependent instrumentation tests.

## Pre-commit

Install hooks:

```bash
make setup-precommit
```

Run all configured hooks:

```bash
make precommit
```

## Recommended Development Workflow

During normal development:

```bash
make
make android-test
```

When modifying cryptography, synchronization, persistence, or concurrency:

```bash
make
make android-test
make android-performance-test
make android-memory-test
```

Before a release:

```bash
make
make precommit
make android-full-test
```

The full release run is intentionally slower because it includes the performance and soak workloads.

## Release Expectations

A release candidate should not be published unless:

- formatting passes
- linting passes
- JVM unit tests pass
- instrumentation tests compile
- standard Android tests pass
- security tests pass
- storage-security tests pass
- end-to-end synchronization passes
- the full instrumentation suite passes
- no significant performance regression has been identified
- no obvious unbounded memory growth has been observed
- the library builds successfully

Performance and memory observations should always be interpreted relative to the documented benchmark environment.
