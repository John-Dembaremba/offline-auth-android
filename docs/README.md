## Testing

The project includes JVM unit tests and Android instrumentation tests covering credential parsing, synchronization, Android Keystore integration, encrypted storage, authentication, rollback protection, malformed payload handling, security failure paths, performance, memory behaviour, and stress testing.

Run the standard project checks:

```bash
make
```

Run normal Android instrumentation tests:

```bash
make android-test
```

Run the complete Android release test suite, including performance and stress tests:

```bash
make android-full-test
```

Additional benchmark targets are available:

```bash
make android-performance-test
make android-parallelism-test
make android-memory-test
make android-stress-test
make android-mixed-failure-test
```

See [Testing](docs/TESTING.md) for the complete testing strategy and [Performance](docs/PERFORMANCE.md) for benchmark results and interpretation.
