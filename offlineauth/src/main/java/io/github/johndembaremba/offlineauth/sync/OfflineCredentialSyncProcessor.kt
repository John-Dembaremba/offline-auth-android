package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.storage.CredentialStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Processes synchronized credential envelopes and applies the resulting
 * authoritative snapshot to local storage.
 *
 * Successful credentials replace their existing local credentials.
 * Failed credentials preserve their existing local credentials.
 * Users missing from the authoritative snapshot are removed.
 */
class OfflineCredentialSyncProcessor(
    private val envelopeProcessor: CredentialEnvelopeProcessor,
    private val credentialStore: CredentialStore,
    private val parallelismStrategy: ParallelismStrategy =
        AdaptiveParallelismStrategy(),
    private val dispatcher: CoroutineDispatcher =
        Dispatchers.Default,
) : CredentialSyncProcessor {
    override suspend fun process(credentials: List<SynchronizedCredential>): CredentialSyncResult {
        val snapshotUsernames =
            credentials
                .map { it.username }
                .toSet()

        val parallelism =
            parallelismStrategy.determine(
                credentialCount = credentials.size,
            )

        val processingDispatcher =
            dispatcher.limitedParallelism(
                parallelism,
            )

        val results =
            coroutineScope {
                credentials
                    .map { synchronizedCredential ->
                        async(processingDispatcher) {
                            processCredentialSafely(
                                synchronizedCredential,
                            )
                        }
                    }.awaitAll()
            }

        val successfulCredentials =
            results
                .filterIsInstance<CredentialProcessingResult.Success>()
                .associate { result ->
                    result.username to result.credential
                }

        val failures =
            results
                .filterIsInstance<CredentialProcessingResult.Failure>()
                .map { result ->
                    CredentialSyncFailure(
                        username = result.username,
                        reason = result.reason,
                    )
                }

        credentialStore.applySnapshot(
            successfulCredentials = successfulCredentials,
            snapshotUsernames = snapshotUsernames,
        )

        return CredentialSyncResult(
            successfulUsernames =
                successfulCredentials.keys.toList(),
            failures = failures,
        )
    }

    private fun processCredentialSafely(synchronizedCredential: SynchronizedCredential): CredentialProcessingResult =
        try {
            val credential =
                envelopeProcessor.process(
                    envelope = synchronizedCredential.envelope,
                )

            CredentialProcessingResult.Success(
                username = synchronizedCredential.username,
                credential = credential,
            )
        } catch (exception: Exception) {
            CredentialProcessingResult.Failure(
                username = synchronizedCredential.username,
                reason =
                    exception.message
                        ?: DEFAULT_FAILURE_REASON,
            )
        }

    companion object {
        private const val DEFAULT_FAILURE_REASON =
            "credential_processing_failed"
    }
}

data class SynchronizedCredential(
    val username: String,
    val envelope: EncryptedEnvelope,
)

data class CredentialSyncResult(
    val successfulUsernames: List<String>,
    val failures: List<CredentialSyncFailure>,
)

data class CredentialSyncFailure(
    val username: String,
    val reason: String,
)

private sealed interface CredentialProcessingResult {
    data class Success(
        val username: String,
        val credential: OfflineCredential,
    ) : CredentialProcessingResult

    data class Failure(
        val username: String,
        val reason: String,
    ) : CredentialProcessingResult
}
