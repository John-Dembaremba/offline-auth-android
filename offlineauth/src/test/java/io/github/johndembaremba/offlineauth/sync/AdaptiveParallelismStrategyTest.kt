package io.github.johndembaremba.offlineauth.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveParallelismStrategyTest {
    @Test
    fun determine_returnsOneForSmallBatch() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 8,
            )

        assertEquals(
            1,
            strategy.determine(
                credentialCount = 5,
            ),
        )
    }

    @Test
    fun determine_returnsTwoForMediumBatch() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 8,
            )

        assertEquals(
            2,
            strategy.determine(
                credentialCount = 10,
            ),
        )
    }

    @Test
    fun determine_returnsFourForLargeBatch() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 8,
            )

        assertEquals(
            4,
            strategy.determine(
                credentialCount = 20,
            ),
        )
    }

    @Test
    fun determine_doesNotExceedAvailableProcessors() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 2,
            )

        assertEquals(
            2,
            strategy.determine(
                credentialCount = 100,
            ),
        )
    }

    @Test
    fun determine_doesNotExceedConfiguredMaximum() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 8,
                maxParallelism = 2,
            )

        assertEquals(
            2,
            strategy.determine(
                credentialCount = 100,
            ),
        )
    }

    @Test
    fun determine_neverReturnsLessThanOne() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 0,
                maxParallelism = 0,
            )

        assertEquals(
            1,
            strategy.determine(
                credentialCount = 100,
            ),
        )
    }

    @Test
    fun determine_usesSequentialProcessingBelowTenUsers() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 8,
            )

        assertEquals(
            1,
            strategy.determine(
                credentialCount = 9,
            ),
        )
    }

    @Test
    fun determine_usesTwoWorkersBelowTwentyUsers() {
        val strategy =
            AdaptiveParallelismStrategy(
                availableProcessors = 8,
            )

        assertEquals(
            2,
            strategy.determine(
                credentialCount = 19,
            ),
        )
    }
}
