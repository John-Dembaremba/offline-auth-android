## Testing

The project includes JVM unit tests and Android instrumentation tests covering credential parsing, synchronization, Android Keystore integration, encrypted storage, authentication, rollback protection, malformed payload handling, security failure paths, Android-version compatibility, performance, memory behaviour, and stress testing.

Run the standard project checks:

```bash
make
```

Run the normal Android compatibility and security instrumentation suite:

```bash
make android-test
```

The standard Android suite intentionally excludes the expensive benchmark and stress workloads.

Run all heavy Android benchmark and stress suites:

```bash
make android-heavy-test
```

Individual heavy test targets are also available:

```bash
make android-performance-test
make android-parallelism-test
make android-memory-test
make android-stress-test
make android-mixed-failure-test
```

Run the complete Android release test suite, including all standard, performance, memory, and stress tests:

```bash
make android-full-test
```

The library currently supports Android API 23 and newer.

Android API 23–25 use a PBKDF2-HMAC-SHA256 compatibility implementation based on `HmacSHA256`, while Android API 26 and newer use the platform `PBKDF2WithHmacSHA256` implementation.

The API 23 compatibility suite, security tests, Android Keystore tests, storage-security tests, PBKDF2 verification, and full stress suite have completed successfully.

See [Testing](docs/TESTING.md) for the complete testing strategy and compatibility matrix, and [Performance](docs/PERFORMANCE.md) for benchmark results and interpretation.
