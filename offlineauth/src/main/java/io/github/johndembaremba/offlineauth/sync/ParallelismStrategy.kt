package io.github.johndembaremba.offlineauth.sync

/**
 * Determines how many credential-processing operations may run concurrently.
 */
fun interface ParallelismStrategy {
    fun determine(credentialCount: Int): Int
}
