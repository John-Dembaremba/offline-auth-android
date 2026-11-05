package io.github.johndembaremba.offlineauth.sync

/**
 * Selects bounded parallelism based on the credential count and device CPU.
 *
 * Small batches are processed sequentially, while larger batches use limited
 * concurrency. Parallelism never exceeds the available processor count or
 * the configured maximum.
 */
class AdaptiveParallelismStrategy(
    private val availableProcessors: Int =
        Runtime.getRuntime().availableProcessors(),
    private val maxParallelism: Int = DEFAULT_MAX_PARALLELISM,
) : ParallelismStrategy {
    override fun determine(credentialCount: Int): Int {
        val desiredParallelism =
            when {
                credentialCount < TEN_USER_THRESHOLD -> 1
                credentialCount < TWENTY_USER_THRESHOLD -> 2
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
        private const val TEN_USER_THRESHOLD = 10
        private const val TWENTY_USER_THRESHOLD = 20
        private const val DEFAULT_MAX_PARALLELISM = 4
    }
}
