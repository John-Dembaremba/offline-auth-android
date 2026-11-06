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
    override suspend fun process(
        credentials: List<SynchronizedCredential>,
        snapshotUsernames: Set<String>,
    ): CredentialSyncResult {
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

        val processingSuccesses =
            results
                .filterIsInstance<CredentialProcessingResult.Success>()

        val processingFailures =
            results
                .filterIsInstance<CredentialProcessingResult.Failure>()
                .map { result ->
                    CredentialSyncFailure(
                        username = result.username,
                        reason = result.reason,
                    )
                }

        val acceptedCredentials =
            mutableMapOf<String, OfflineCredential>()

        val rollbackFailures =
            mutableListOf<CredentialSyncFailure>()

        processingSuccesses.forEach { result ->
            val existingCredential =
                credentialStore.get(
                    username = result.username,
                )

            if (
                existingCredential != null &&
                result.credential.credentialVersion <
                existingCredential.credentialVersion
            ) {
                val exception =
                    CredentialVersionRollbackException(
                        username = result.username,
                        storedCredentialVersion =
                            existingCredential.credentialVersion,
                        incomingCredentialVersion =
                            result.credential.credentialVersion,
                    )

                rollbackFailures +=
                    CredentialSyncFailure(
                        username = exception.username,
                        reason =
                            CredentialProcessingFailureReason
                                .CREDENTIAL_VERSION_ROLLBACK
                                .code,
                        storedCredentialVersion =
                            exception.storedCredentialVersion,
                        incomingCredentialVersion =
                            exception.incomingCredentialVersion,
                    )
            } else {
                acceptedCredentials[result.username] =
                    result.credential
            }
        }

        credentialStore.applySnapshot(
            successfulCredentials = acceptedCredentials,
            snapshotUsernames = snapshotUsernames,
        )

        return CredentialSyncResult(
            successfulUsernames =
                acceptedCredentials.keys.toList(),
            failures =
                processingFailures + rollbackFailures,
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
        } catch (exception: CredentialProcessingException) {
            CredentialProcessingResult.Failure(
                username = synchronizedCredential.username,
                reason = exception.reason.code,
            )
        } catch (_: Exception) {
            CredentialProcessingResult.Failure(
                username = synchronizedCredential.username,
                reason =
                    CredentialProcessingFailureReason
                        .PROCESSING_FAILED
                        .code,
            )
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
    val storedCredentialVersion: Int? = null,
    val incomingCredentialVersion: Int? = null,
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
