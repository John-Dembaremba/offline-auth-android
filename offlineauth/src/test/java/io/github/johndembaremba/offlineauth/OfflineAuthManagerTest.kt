package io.github.johndembaremba.offlineauth

import io.github.johndembaremba.offlineauth.auth.OfflineAuthenticator
import io.github.johndembaremba.offlineauth.crypto.PublicKeyProvider
import io.github.johndembaremba.offlineauth.model.CredentialFailurePayload
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.storage.CredentialStore
import io.github.johndembaremba.offlineauth.sync.CredentialSyncProcessor
import io.github.johndembaremba.offlineauth.sync.CredentialSyncResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OfflineAuthManagerTest {
    @Test
    fun getPublicKey_returnsProviderPublicKey() {
        val manager =
            manager(
                publicKeyProvider =
                    PublicKeyProvider {
                        "test-public-key"
                    },
            )

        assertEquals(
            "test-public-key",
            manager.getPublicKey(),
        )
    }

    @Test
    fun authenticate_returnsFalseWhenCredentialDoesNotExist() {
        val manager = manager()

        assertFalse(
            manager.authenticate(
                username = "missing-user",
                password = "password".toCharArray(),
            ),
        )
    }

    @Test
    fun sync_returnsProcessorResult() =
        runBlocking {
            val expected =
                CredentialSyncResult(
                    successfulUsernames =
                        listOf(
                            "alice",
                            "bob",
                        ),
                    failures = emptyList(),
                )

            val manager =
                manager(
                    syncProcessor =
                        CredentialSyncProcessor { _, _ ->
                            expected
                        },
                )

            val snapshot =
                OfflineCredentialSnapshot(
                    protocolVersion = 1,
                    credentials = emptyList(),
                    failures = emptyList(),
                )

            assertEquals(
                expected,
                manager.sync(
                    snapshot = snapshot,
                ),
            )
        }

    @Test
    fun sync_includesBackendFailureUserInAuthoritativeSnapshot() =
        runBlocking {
            var capturedSnapshotUsernames: Set<String> =
                emptySet()

            val manager =
                manager(
                    syncProcessor =
                        CredentialSyncProcessor { _, snapshotUsernames ->
                            capturedSnapshotUsernames =
                                snapshotUsernames

                            CredentialSyncResult(
                                successfulUsernames = emptyList(),
                                failures = emptyList(),
                            )
                        },
                )

            val snapshot =
                OfflineCredentialSnapshot(
                    protocolVersion = 1,
                    credentials = emptyList(),
                    failures =
                        listOf(
                            CredentialFailurePayload(
                                userId = "1",
                                username = "bob",
                                reason = "unusable_password",
                            ),
                        ),
                )

            val result =
                manager.sync(
                    snapshot = snapshot,
                )

            assertEquals(
                setOf("bob"),
                capturedSnapshotUsernames,
            )

            assertEquals(
                "bob",
                result.failures.first().username,
            )

            assertEquals(
                "unusable_password",
                result.failures.first().reason,
            )
        }

    private fun manager(
        publicKeyProvider: PublicKeyProvider =
            PublicKeyProvider {
                "public-key"
            },
        credentialStore: CredentialStore =
            FakeCredentialStore(),
        syncProcessor: CredentialSyncProcessor =
            CredentialSyncProcessor { _, _ ->
                CredentialSyncResult(
                    successfulUsernames = emptyList(),
                    failures = emptyList(),
                )
            },
    ): OfflineAuthManager =
        OfflineAuthManager(
            publicKeyProvider = publicKeyProvider,
            credentialStore = credentialStore,
            authenticator = OfflineAuthenticator(),
            syncProcessor = syncProcessor,
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

        credentials.putAll(
            successfulCredentials,
        )
    }

    override fun clear() {
        credentials.clear()
    }
}
