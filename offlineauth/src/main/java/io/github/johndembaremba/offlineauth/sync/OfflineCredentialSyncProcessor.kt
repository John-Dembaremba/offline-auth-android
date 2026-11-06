package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.model.CredentialSyncFailure
import io.github.johndembaremba.offlineauth.model.CredentialSyncFailureReason
import io.github.johndembaremba.offlineauth.model.CredentialSyncResult
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.LocalCredentialSyncFailureReason
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
internal class OfflineCredentialSyncProcessor(
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
                        reason =
                            CredentialSyncFailureReason.Local(
                                reason = result.reason,
                            ),
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
                rollbackFailures +=
                    CredentialSyncFailure(
                        username = result.username,
                        reason =
                            CredentialSyncFailureReason.Local(
                                reason =
                                    LocalCredentialSyncFailureReason
                                        .CREDENTIAL_VERSION_ROLLBACK,
                            ),
                        storedCredentialVersion =
                            existingCredential.credentialVersion,
                        incomingCredentialVersion =
                            result.credential.credentialVersion,
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
                reason = exception.reason,
            )
        } catch (_: Exception) {
            CredentialProcessingResult.Failure(
                username = synchronizedCredential.username,
                reason =
                    LocalCredentialSyncFailureReason
                        .PROCESSING_FAILED,
            )
        }
}

internal data class SynchronizedCredential(
    val username: String,
    val envelope: EncryptedEnvelope,
)

private sealed interface CredentialProcessingResult {
    data class Success(
        val username: String,
        val credential: OfflineCredential,
    ) : CredentialProcessingResult

    data class Failure(
        val username: String,
        val reason: LocalCredentialSyncFailureReason,
    ) : CredentialProcessingResult
}
