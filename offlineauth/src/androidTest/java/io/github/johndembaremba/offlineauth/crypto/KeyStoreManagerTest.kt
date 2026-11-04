package io.github.johndembaremba.offlineauth.crypto

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyStore
import java.security.spec.X509EncodedKeySpec

@RunWith(AndroidJUnit4::class)
class KeyStoreManagerTest {
    private val testAlias = "offline_auth_test_key"

    @After
    fun tearDown() {
        val keyStore =
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }

        if (keyStore.containsAlias(testAlias)) {
            keyStore.deleteEntry(testAlias)
        }
    }

    @Test
    fun getOrCreatePublicKey_generatesValidRsaPublicKey() {
        val manager = KeyStoreManager(testAlias)

        val encodedPublicKey = manager.getOrCreatePublicKey()

        val decoded =
            Base64.decode(
                encodedPublicKey,
                Base64.NO_WRAP,
            )

        val publicKey =
            KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(decoded),
            )

        assertNotNull(publicKey)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun getOrCreatePublicKey_reusesExistingKeyPair() {
        val manager = KeyStoreManager(testAlias)

        val firstPublicKey =
            Base64.decode(
                manager.getOrCreatePublicKey(),
                Base64.NO_WRAP,
            )

        val secondPublicKey =
            Base64.decode(
                manager.getOrCreatePublicKey(),
                Base64.NO_WRAP,
            )

        assertArrayEquals(
            firstPublicKey,
            secondPublicKey,
        )
    }
}
