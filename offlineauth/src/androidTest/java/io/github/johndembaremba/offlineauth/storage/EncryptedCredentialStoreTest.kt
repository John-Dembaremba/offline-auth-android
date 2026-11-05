package io.github.johndembaremba.offlineauth.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedCredentialStoreTest {
    private val context: Context =
        ApplicationProvider.getApplicationContext()

    private val store =
        EncryptedCredentialStore(
            context = context,
            storageKeyManager =
                StorageKeyManager(
                    keyAlias = TEST_KEY_ALIAS,
                ),
        )

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun saveAndGet_returnsCredential() {
        val credential = credential()

        store.save(
            username = "test-user",
            credential = credential,
        )

        val stored =
            store.get(
                username = "test-user",
            )

        assertEquals(
            credential,
            stored,
        )
    }

    @Test
    fun get_returnsNullForUnknownUser() {
        assertNull(
            store.get(
                username = "missing-user",
            ),
        )
    }

    @Test
    fun remove_deletesCredential() {
        store.save(
            username = "test-user",
            credential = credential(),
        )

        store.remove(
            username = "test-user",
        )

        assertNull(
            store.get(
                username = "test-user",
            ),
        )
    }

    @Test
    fun clear_removesAllCredentials() {
        store.save(
            username = "user-one",
            credential = credential(),
        )

        store.save(
            username = "user-two",
            credential =
                credential(
                    credentialVersion = 2,
                ),
        )

        store.clear()

        assertNull(store.get("user-one"))
        assertNull(store.get("user-two"))
    }

    @Test
    fun replaceAll_removesStaleCredentials() {
        store.save(
            username = "old-user",
            credential = credential(),
        )

        store.replaceAll(
            mapOf(
                "user-one" to credential(),
                "user-two" to
                    credential(
                        credentialVersion = 2,
                    ),
            ),
        )

        assertNull(
            store.get(
                username = "old-user",
            ),
        )

        assertEquals(
            credential(),
            store.get(
                username = "user-one",
            ),
        )

        assertEquals(
            credential(
                credentialVersion = 2,
            ),
            store.get(
                username = "user-two",
            ),
        )
    }

    private fun credential(credentialVersion: Int = 1): OfflineCredential =
        OfflineCredential(
            passwordAlgorithm = "pbkdf2_sha256",
            passwordVerifier =
                "pbkdf2_sha256${'$'}100000${'$'}salt${'$'}hash",
            credentialVersion = credentialVersion,
            issuedAt = "2026-11-05T08:00:00Z",
            expiresAt = "2026-11-06T08:00:00Z",
        )

    companion object {
        private const val TEST_KEY_ALIAS =
            "offline_auth_storage_test_key"
    }
}
