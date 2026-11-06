# Performance

This document records observed performance characteristics of `offline-auth-android`.

The results in this document are measurements from the project test environment. They are intended to provide a baseline for development and regression testing rather than guaranteed performance figures for every Android device.

## Test Environment

The primary benchmark environment used:

- Android emulator
- Android API 37
- x86_64 system image
- KVM hardware acceleration
- 4 available processors
- Linux host
- Android Keystore enabled
- AES-256-GCM credential encryption
- RSA-OAEP-SHA256 key wrapping
- PBKDF2-SHA256 password verification
- Adaptive credential processing parallelism with a maximum of 4 workers

Performance tests measure the Android-side credential synchronization operation.

Where possible, backend-style test data generation and envelope construction are performed outside the timed synchronization section.

## Synchronization Performance

The repeated synchronization benchmark executes each workload five times.

The following measurements were observed during the dedicated benchmark run.

| Credentials | Average | Median | Minimum | Maximum | Average per credential |
|---:|---:|---:|---:|---:|---:|
| 1 | 19.783 ms | 19.652 ms | 11.684 ms | 27.070 ms | 19.783 ms |
| 10 | 91.038 ms | 95.535 ms | 70.122 ms | 97.983 ms | 9.104 ms |
| 50 | 358.761 ms | 374.039 ms | 275.464 ms | 393.979 ms | 7.175 ms |
| 100 | 743.594 ms | 771.386 ms | 626.186 ms | 791.068 ms | 7.436 ms |
| 500 | 3632.967 ms | 3115.496 ms | 3023.136 ms | 5375.898 ms | 7.266 ms |
| 1000 | 6168.505 ms | 6266.432 ms | 5175.199 ms | 7129.778 ms | 6.169 ms |

All credentials in these benchmark workloads synchronized successfully.

### Interpretation

The results show approximately linear scaling for larger snapshots.

For the tested environment:

- 100 credentials typically synchronized in under one second.
- 500 credentials typically synchronized in approximately 3–4 seconds.
- 1000 credentials typically synchronized in approximately 6 seconds.
- Per-credential processing cost decreased as batch size increased.

These figures should not be interpreted as hard service-level guarantees. Performance varies depending on CPU speed, Android version, Keystore implementation, system load, storage performance, thermal conditions, and device architecture.

## Parallelism

The library uses bounded parallel credential processing.

Parallelism levels of 1, 2, and 4 were benchmarked using workloads of 100, 500, and 1000 credentials.

### 100 credentials

| Parallelism | Average | Median | Average per credential |
|---:|---:|---:|---:|
| 1 | 1601.653 ms | 1021.768 ms | 16.017 ms |
| 2 | 752.847 ms | 793.206 ms | 7.528 ms |
| 4 | 660.016 ms | 718.567 ms | 6.600 ms |

### 500 credentials

| Parallelism | Average | Median | Average per credential |
|---:|---:|---:|---:|
| 1 | 4524.926 ms | 4446.503 ms | 9.050 ms |
| 2 | 3519.362 ms | 3679.938 ms | 7.039 ms |
| 4 | 3238.314 ms | 3336.075 ms | 6.477 ms |

### 1000 credentials

| Parallelism | Average | Median | Average per credential |
|---:|---:|---:|---:|
| 1 | 8511.269 ms | 8929.665 ms | 8.511 ms |
| 2 | 8682.037 ms | 7345.377 ms | 8.682 ms |
| 4 | 6808.043 ms | 6700.003 ms | 6.808 ms |

Parallelism level `4` produced the lowest median synchronization time for all tested workload sizes.

Compared with serial execution, the median improvement was approximately:

- 30% for 100 credentials
- 25% for 500 credentials
- 25% for 1000 credentials

The current adaptive strategy therefore remains capped at four workers.

Increasing concurrency beyond four has not been demonstrated to improve performance and could increase contention, memory pressure, or Android Keystore overhead.

## Memory Behaviour

Memory measurements use Android process PSS and include garbage collection checkpoints around synchronization.

### 100 credentials

- Baseline: 31.20 MB
- Before synchronization: 30.23 MB
- After synchronization: 31.65 MB
- After GC: 31.40 MB
- Synchronization delta: 1.42 MB
- Retained delta: 1.17 MB

### 500 credentials

- Baseline: 31.17 MB
- Before synchronization: 31.66 MB
- After synchronization: 33.24 MB
- After GC: 32.47 MB
- Synchronization delta: 1.58 MB
- Retained delta: 0.81 MB

### 1000 credentials

- Baseline: 31.59 MB
- Before synchronization: 32.49 MB
- After synchronization: 34.18 MB
- After GC: 34.02 MB
- Synchronization delta: 1.69 MB
- Retained delta: 1.53 MB

The temporary memory increase remained below approximately 2 MB for snapshots containing up to 1000 credentials in this environment.

These measurements do not prove the absence of all possible memory leaks. They show that no obvious unbounded memory growth was observed during the measured synchronization workloads.

## Stress and Soak Testing

Repeated synchronization testing was performed using the following workloads:

| Credentials | Cycles | Credential sync operations |
|---:|---:|---:|
| 100 | 100 | 10,000 |
| 500 | 50 | 25,000 |
| 1000 | 25 | 25,000 |

Total credential synchronization operations:

**60,000**

### 100 credentials × 100 cycles

- Average sync: 639.549 ms
- Minimum: 417.116 ms
- Maximum: 1057.418 ms
- Baseline memory: 30.380 MB
- Peak memory: 32.140 MB
- Final memory: 28.739 MB
- Retained delta: -1.641 MB

### 500 credentials × 50 cycles

- Average sync: 3355.544 ms
- Minimum: 2111.388 ms
- Maximum: 5353.536 ms
- Baseline memory: 29.067 MB
- Peak memory: 32.638 MB
- Final memory: 30.431 MB
- Retained delta: 1.363 MB

### 1000 credentials × 25 cycles

- Average sync: 6490.808 ms
- Minimum: 5188.696 ms
- Maximum: 8322.732 ms
- Baseline memory: 30.079 MB
- Peak memory: 37.091 MB
- Final memory: 31.927 MB
- Retained delta: 1.848 MB

No correctness failures, crashes, or obvious unbounded memory accumulation were observed during these workloads.

## Mixed-Failure Performance

A 1000-user authoritative snapshot was tested with deliberately mixed results:

- 900 valid credentials
- 50 backend-reported failures
- 30 corrupted encrypted credentials
- 20 credential-version rollback attempts

Observed result:

- 900 successful synchronizations
- 50 backend failures
- 30 decryption failures
- 20 rollback failures
- 100 total failures
- approximately 3.9–4.3 seconds synchronization time during observed runs

The library continued processing valid credentials while preserving correct failure semantics.

## Full-Suite Performance

Performance figures collected while the entire instrumentation suite is running can be noticeably slower and more variable than isolated benchmark results.

For example, during a full instrumentation run, the 1000-user benchmark reported:

- Average: 8286.436 ms
- Median: 8559.746 ms
- Minimum: 6810.276 ms
- Maximum: 10276.360 ms

All 1000 credentials still synchronized successfully in every run.

This difference is expected because the emulator has already performed memory tests, cryptographic work, parallelism benchmarks, and other instrumentation before the benchmark executes.

For this reason:

**Dedicated benchmark runs should be used for performance comparisons.**

The full instrumentation suite should primarily be treated as correctness and release-gate evidence.

## Recommended Parallelism

The current adaptive strategy is:

- fewer than 10 credentials: 1 worker
- fewer than 20 credentials: 2 workers
- 20 or more credentials: up to 4 workers
- always limited by available processors
- maximum parallelism: 4

Current test results support keeping this strategy.

## Running Performance Tests

Run the standard synchronization benchmark:

```bash
make android-performance-test
```

Run the parallelism benchmark:

```bash
make android-parallelism-test
```

Run memory measurements:

```bash
make android-memory-test
```

Run the repeated soak test:

```bash
make android-stress-test
```

Run the mixed-failure workload:

```bash
make android-mixed-failure-test
```

Run all instrumentation tests, including the expensive benchmark suites:

```bash
make android-full-test
```

## Benchmark Guidance

When comparing performance between versions:

1. Use the same emulator or physical device.
2. Use the same Android API level.
3. Keep processor and memory configuration unchanged.
4. Run benchmarks separately from the full instrumentation suite.
5. Prefer medians over individual runs.
6. Compare multiple runs rather than a single measurement.
7. Record changes to cryptography, storage, parallelism, or Android Keystore usage.

A performance regression should be investigated when a repeatable change is observed under comparable conditions.

## Current Performance Conclusion

Under the tested 4-core Android emulator environment, the library comfortably handles snapshots containing up to 1000 offline credentials.

The current implementation demonstrates:

- bounded parallel processing
- approximately linear synchronization scaling
- low temporary memory growth
- stable repeated synchronization
- correct partial-failure handling
- successful processing of large authoritative snapshots

These results establish a baseline for future releases rather than an absolute performance guarantee.
