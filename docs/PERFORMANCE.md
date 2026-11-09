# Performance

This document records observed performance characteristics of `offline-auth-android`.

These results are development and regression baselines, not guaranteed performance figures for every Android device.

## Benchmark Environment

Primary performance measurements were collected on:

- Android API 37 emulator
- x86_64 system image
- KVM acceleration
- 4 available processors
- Linux host
- Android Keystore
- AES-256-GCM
- RSA-OAEP-SHA256
- PBKDF2-SHA256
- adaptive parallelism capped at 4 workers

Backend-style fixture generation and encrypted-envelope construction are performed outside the timed synchronization section where possible.

## Synchronization Performance

Each workload was executed five times.

| Credentials | Average | Median | Minimum | Maximum | Avg / credential |
|---:|---:|---:|---:|---:|---:|
| 1 | 19.783 ms | 19.652 ms | 11.684 ms | 27.070 ms | 19.783 ms |
| 10 | 91.038 ms | 95.535 ms | 70.122 ms | 97.983 ms | 9.104 ms |
| 50 | 358.761 ms | 374.039 ms | 275.464 ms | 393.979 ms | 7.175 ms |
| 100 | 743.594 ms | 771.386 ms | 626.186 ms | 791.068 ms | 7.436 ms |
| 500 | 3632.967 ms | 3115.496 ms | 3023.136 ms | 5375.898 ms | 7.266 ms |
| 1000 | 6168.505 ms | 6266.432 ms | 5175.199 ms | 7129.778 ms | 6.169 ms |

All credentials synchronized successfully.

Observed API 37 behavior:

- 100 credentials: under 1 second
- 500 credentials: about 3–4 seconds
- 1000 credentials: about 6 seconds
- scaling is approximately linear for larger snapshots

Performance will vary by CPU, Android version, device architecture, Keystore implementation, storage, and system load.

## Parallelism

Fixed parallelism levels of 1, 2, and 4 were benchmarked.

### 100 credentials

| Parallelism | Average | Median |
|---:|---:|---:|
| 1 | 1601.653 ms | 1021.768 ms |
| 2 | 752.847 ms | 793.206 ms |
| 4 | 660.016 ms | 718.567 ms |

### 500 credentials

| Parallelism | Average | Median |
|---:|---:|---:|
| 1 | 4524.926 ms | 4446.503 ms |
| 2 | 3519.362 ms | 3679.938 ms |
| 4 | 3238.314 ms | 3336.075 ms |

### 1000 credentials

| Parallelism | Average | Median |
|---:|---:|---:|
| 1 | 8511.269 ms | 8929.665 ms |
| 2 | 8682.037 ms | 7345.377 ms |
| 4 | 6808.043 ms | 6700.003 ms |

Parallelism `4` produced the lowest median time at every tested workload size.

The current adaptive strategy remains:

```text
< 10 credentials   → 1 worker
< 20 credentials   → 2 workers
20+ credentials    → up to 4 workers
maximum            → 4 workers
```

## Memory Behaviour

Android process PSS was measured around synchronization.

| Credentials | Before sync | After sync | After GC | Retained delta |
|---:|---:|---:|---:|---:|
| 100 | 30.23 MB | 31.65 MB | 31.40 MB | 1.17 MB |
| 500 | 31.66 MB | 33.24 MB | 32.47 MB | 0.81 MB |
| 1000 | 32.49 MB | 34.18 MB | 34.02 MB | 1.53 MB |

Temporary memory growth remained below approximately 2 MB for workloads up to 1000 credentials.

No obvious unbounded memory growth was observed.

## Stress Testing

Repeated synchronization workloads:

| Credentials | Cycles | Sync operations |
|---:|---:|---:|
| 100 | 100 | 10,000 |
| 500 | 50 | 25,000 |
| 1000 | 25 | 25,000 |

Total:

**60,000 credential synchronization operations**

Observed averages:

| Credentials | Average sync | Peak memory | Retained delta |
|---:|---:|---:|---:|
| 100 | 639.549 ms | 32.140 MB | -1.641 MB |
| 500 | 3355.544 ms | 32.638 MB | 1.363 MB |
| 1000 | 6490.808 ms | 37.091 MB | 1.848 MB |

No correctness failures, crashes, or obvious unbounded memory accumulation were observed.

## Mixed-Failure Workload

A 1000-user snapshot was tested with:

```text
900 valid credentials
50 backend failures
30 corrupted encrypted credentials
20 rollback attempts
```

Observed result:

```text
900 successful
50 backend failures
30 decryption failures
20 rollback failures
100 total failures
```

Synchronization completed in approximately **3.9–4.3 seconds** during observed API 37 runs.

Valid credentials continued processing correctly while failure semantics were preserved.

## Full-Suite Performance

Full instrumentation runs are slower and more variable than isolated benchmarks.

One full API 37 run reported for 1000 credentials:

```text
Average: 8286.436 ms
Median: 8559.746 ms
Minimum: 6810.276 ms
Maximum: 10276.360 ms
```

Dedicated benchmark runs should therefore be used for performance comparisons.

The full instrumentation suite should primarily be treated as correctness and release-gate evidence.

## Android Compatibility

The current compatibility matrix is:

| Android version | API | Status | Notes |
|---|---:|---|---|
| Android 6.0 | 23 | ✅ Passed | PBKDF2-HMAC-SHA256 fallback validated |
| Android 9 | 28 | ✅ Passed | Native PBKDF2 path validated |
| Android 13 | 33 | ✅ Passed | Standard compatibility suite validated |
| Android 17 test image | 37 | ✅ Passed | Primary benchmark environment |

Production PBKDF2 behavior:

```text
API 23–25
    PBKDF2-HMAC-SHA256 using HmacSHA256

API 26+
    SecretKeyFactory("PBKDF2WithHmacSHA256")
```

Both implementations preserve the Django-compatible PBKDF2-SHA256 verifier format.

The standard compatibility suite completed in approximately:

```text
API 23   36 seconds
API 28   22 seconds
API 33   33 seconds
```

These timings are test-suite timings, not synchronization benchmarks.

API 23 also completed the full performance/stress instrumentation suite successfully.

## Running Performance Tests

Standard compatibility suite:

```bash
make android-test
```

Dedicated benchmarks:

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

## Known Android Test Tooling Warning

API 23 connected tests may report:

```text
Failed to retrieve additional test outputs from device.
java.io.IOException: File name too long
```

The warning originates from Android tooling's `AndroidAdditionalTestOutputCollector`.

Observed runs still finish with:

```text
BUILD SUCCESSFUL
```

so it is currently treated as a test-output collection issue rather than a library correctness failure.

## Benchmark Guidance

For meaningful comparisons:

1. use the same device or emulator
2. use the same API level
3. keep CPU and memory configuration unchanged
4. use the same system image architecture
5. run benchmarks separately from the full suite
6. compare multiple runs and prefer medians
7. keep host load as consistent as possible
8. record cryptography, storage, parallelism, or Keystore changes

A regression should be investigated when it is repeatable under comparable conditions.

## Conclusion

The current implementation demonstrates:

- approximately linear synchronization scaling
- bounded parallel processing
- low temporary memory growth
- successful 60,000-operation soak testing
- correct mixed-failure handling
- successful synchronization of snapshots up to 1000 credentials
- compatibility across API 23, 28, 33, and 37
- Django-compatible PBKDF2 verification across old and new Android implementations

These results establish a development baseline rather than an absolute performance guarantee.
