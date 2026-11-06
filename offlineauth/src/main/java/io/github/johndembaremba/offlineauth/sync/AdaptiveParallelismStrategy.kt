package io.github.johndembaremba.offlineauth.sync

/**
 * Selects a conservative level of parallelism based on credential count
 * and the number of processors available on the device.
 */
internal class AdaptiveParallelismStrategy(
    private val availableProcessors: Int =
        Runtime.getRuntime().availableProcessors(),
    private val maxParallelism: Int = DEFAULT_MAX_PARALLELISM,
) : ParallelismStrategy {
    override fun determine(credentialCount: Int): Int {
        val desiredParallelism =
            when {
                credentialCount < 10 -> 1
                credentialCount < 20 -> 2
                else -> 4
            }

        return desiredParallelism
            .coerceAtMost(
                availableProcessors.coerceAtLeast(1),
            ).coerceAtMost(
                maxParallelism.coerceAtLeast(1),
            ).coerceAtLeast(1)
    }

    companion object {
        private const val DEFAULT_MAX_PARALLELISM = 4
    }
}
