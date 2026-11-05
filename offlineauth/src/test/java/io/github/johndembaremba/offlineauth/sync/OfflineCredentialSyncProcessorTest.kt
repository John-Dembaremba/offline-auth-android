package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.storage.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCredentialSyncProcessorTest {
    @Test
    fun process_returnsSuccessfulAndFailedUsernames() =
        runBlocking {
            val store = FakeCredentialStore()

            val envelopeProcessor =
                CredentialEnvelopeProcessor { envelope ->
                    when (envelope.encryptedKey) {
                        "alice" -> {
                            credential(
                                credentialVersion = 2,
                            )
                        }

                        "bob" -> {
                            throw IllegalStateException(
                                "decryption_failed",
                            )
                        }

                        else -> {
                            credential()
                        }
                    }
                }

            val processor =
                OfflineCredentialSyncProcessor(
                    envelopeProcessor = envelopeProcessor,
                    credentialStore = store,
                    parallelismStrategy =
                        ParallelismStrategy {
                            2
                        },
                    dispatcher = Dispatchers.Default,
                )

            val result =
                processor.process(
                    credentials =
                        listOf(
                            synchronizedCredential(
                                username = "alice",
                            ),
                            synchronizedCredential(
                                username = "bob",
                            ),
                        ),
                    snapshotUsernames =
                        setOf(
                            "alice",
                            "bob",
                        ),
                )

            assertEquals(
                listOf("alice"),
                result.successfulUsernames,
            )

            assertEquals(
                1,
                result.failures.size,
            )

            assertEquals(
                "bob",
                result.failures.first().username,
            )
        }

    @Test
    fun process_preservesFailedUsersExistingCredential() =
        runBlocking {
            val store = FakeCredentialStore()

            val oldBobCredential =
                credential(
                    credentialVersion = 1,
                )

            store.save(
                username = "bob",
                credential = oldBobCredential,
            )

            val envelopeProcessor =
                CredentialEnvelopeProcessor {
                    throw IllegalStateException(
                        "processing_failed",
                    )
                }

            val processor =
                OfflineCredentialSyncProcessor(
                    envelopeProcessor = envelopeProcessor,
                    credentialStore = store,
                    parallelismStrategy =
                        ParallelismStrategy {
                            1
                        },
                    dispatcher = Dispatchers.Default,
                )

            processor.process(
                credentials =
                    listOf(
                        synchronizedCredential(
                            username = "bob",
                        ),
                    ),
                snapshotUsernames =
                    setOf(
                        "bob",
                    ),
            )

            assertEquals(
                oldBobCredential,
                store.get("bob"),
            )
        }

    @Test
    fun process_removesUserMissingFromSnapshot() =
        runBlocking {
            val store = FakeCredentialStore()

            store.save(
                username = "charlie",
                credential = credential(),
            )

            val processor =
                OfflineCredentialSyncProcessor(
                    envelopeProcessor =
                        CredentialEnvelopeProcessor {
                            credential()
                        },
                    credentialStore = store,
                    parallelismStrategy =
                        ParallelismStrategy {
                            1
                        },
                    dispatcher = Dispatchers.Default,
                )

            processor.process(
                credentials =
                    listOf(
                        synchronizedCredential(
                            username = "alice",
                        ),
                    ),
                snapshotUsernames =
                    setOf(
                        "alice",
                    ),
            )

            assertTrue(
                store.get("charlie") == null,
            )
        }

    private fun synchronizedCredential(username: String): SynchronizedCredential =
        SynchronizedCredential(
            username = username,
            envelope =
                EncryptedEnvelope(
                    keyAlgorithm = "RSA-OAEP-256",
                    contentAlgorithm = "A256GCM",
                    encryptedKey = username,
                    nonce = "nonce",
                    ciphertext = "ciphertext",
                ),
        )

    private fun credential(credentialVersion: Int = 1): OfflineCredential =
        OfflineCredential(
            passwordAlgorithm = "pbkdf2_sha256",
            passwordVerifier =
                "pbkdf2_sha256${'$'}100000${'$'}salt${'$'}hash",
            credentialVersion = credentialVersion,
            issuedAt = "2026-11-05T08:00:00Z",
            expiresAt = "2026-11-06T08:00:00Z",
        )
}

private class FakeCredentialStore : CredentialStore {
    private val credentials =
        mutableMapOf<String, OfflineCredential>()

    override fun save(
        username: String,
        credential: OfflineCredential,
    ) {
        credentials[username] = credential
    }

    override fun get(username: String): OfflineCredential? = credentials[username]

    override fun remove(username: String) {
        credentials.remove(username)
    }

    override fun replaceAll(credentials: Map<String, OfflineCredential>) {
        this.credentials.clear()
        this.credentials.putAll(credentials)
    }

    override fun applySnapshot(
        successfulCredentials: Map<String, OfflineCredential>,
        snapshotUsernames: Set<String>,
    ) {
        credentials.keys
            .filter { username ->
                username !in snapshotUsernames
            }.toList()
            .forEach { username ->
                credentials.remove(username)
            }

        successfulCredentials.forEach { (username, credential) ->
            credentials[username] = credential
        }
    }

    override fun clear() {
        credentials.clear()
    }
}
