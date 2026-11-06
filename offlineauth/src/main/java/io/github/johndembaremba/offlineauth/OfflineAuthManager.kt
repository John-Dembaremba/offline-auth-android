package io.github.johndembaremba.offlineauth

import android.content.Context
import io.github.johndembaremba.offlineauth.auth.OfflineAuthenticator
import io.github.johndembaremba.offlineauth.crypto.EnvelopeDecryptor
import io.github.johndembaremba.offlineauth.crypto.KeyStoreManager
import io.github.johndembaremba.offlineauth.crypto.PublicKeyProvider
import io.github.johndembaremba.offlineauth.model.CredentialSyncFailure
import io.github.johndembaremba.offlineauth.model.CredentialSyncFailureReason
import io.github.johndembaremba.offlineauth.model.CredentialSyncResult
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.serialization.CredentialParser
import io.github.johndembaremba.offlineauth.storage.CredentialStore
import io.github.johndembaremba.offlineauth.storage.EncryptedCredentialStore
import io.github.johndembaremba.offlineauth.sync.AdaptiveParallelismStrategy
import io.github.johndembaremba.offlineauth.sync.CredentialSyncProcessor
import io.github.johndembaremba.offlineauth.sync.DefaultCredentialEnvelopeProcessor
import io.github.johndembaremba.offlineauth.sync.OfflineCredentialSyncProcessor
import io.github.johndembaremba.offlineauth.sync.SynchronizedCredential
import io.github.johndembaremba.offlineauth.sync.UnsupportedProtocolVersionException

/**
 * Main entry point for offline authentication.
 */
class OfflineAuthManager internal constructor(
    private val publicKeyProvider: PublicKeyProvider,
    private val credentialStore: CredentialStore,
    private val authenticator: OfflineAuthenticator,
    private val syncProcessor: CredentialSyncProcessor,
) {
    fun getPublicKey(): String = publicKeyProvider.getPublicKey()

    suspend fun sync(snapshot: OfflineCredentialSnapshot): CredentialSyncResult {
        validateProtocolVersion(
            protocolVersion = snapshot.protocolVersion,
        )

        val synchronizedCredentials =
            snapshot.credentials.map { credential ->
                SynchronizedCredential(
                    username = credential.username,
                    envelope = credential.envelope,
                )
            }

        val snapshotUsernames =
            buildSet {
                snapshot.credentials.forEach { credential ->
                    add(credential.username)
                }

                snapshot.failures.forEach { failure ->
                    add(failure.username)
                }
            }

        val processingResult =
            syncProcessor.process(
                credentials = synchronizedCredentials,
                snapshotUsernames = snapshotUsernames,
            )

        val backendFailures =
            snapshot.failures.map { failure ->
                CredentialSyncFailure(
                    username = failure.username,
                    reason =
                        CredentialSyncFailureReason.Backend(
                            reason = failure.reason,
                        ),
                )
            }

        return CredentialSyncResult(
            successfulUsernames =
                processingResult.successfulUsernames,
            failures =
                backendFailures + processingResult.failures,
        )
    }

    fun authenticate(
        username: String,
        password: CharArray,
    ): Boolean {
        val credential =
            credentialStore.get(
                username = username,
            ) ?: return false

        return authenticator.authenticate(
            password = password,
            credential = credential,
        )
    }

    private fun validateProtocolVersion(protocolVersion: Int) {
        if (protocolVersion != SUPPORTED_PROTOCOL_VERSION) {
            throw UnsupportedProtocolVersionException(
                protocolVersion = protocolVersion,
            )
        }
    }

    companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 1

        fun create(context: Context): OfflineAuthManager {
            val applicationContext =
                context.applicationContext

            val keyStoreManager =
                KeyStoreManager()

            val credentialStore =
                EncryptedCredentialStore(
                    context = applicationContext,
                )

            val envelopeProcessor =
                DefaultCredentialEnvelopeProcessor(
                    envelopeDecryptor =
                        EnvelopeDecryptor(
                            keyStoreManager = keyStoreManager,
                        ),
                    credentialParser =
                        CredentialParser(),
                )

            val syncProcessor =
                OfflineCredentialSyncProcessor(
                    envelopeProcessor = envelopeProcessor,
                    credentialStore = credentialStore,
                    parallelismStrategy =
                        AdaptiveParallelismStrategy(),
                )

            return OfflineAuthManager(
                publicKeyProvider = keyStoreManager,
                credentialStore = credentialStore,
                authenticator = OfflineAuthenticator(),
                syncProcessor = syncProcessor,
            )
        }
    }
}
