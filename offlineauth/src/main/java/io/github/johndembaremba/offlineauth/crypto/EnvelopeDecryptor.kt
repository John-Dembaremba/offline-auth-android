package io.github.johndembaremba.offlineauth.crypto

import android.util.Base64
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts offline credential envelopes issued by the backend.
 *
 * The wrapped AES key is decrypted using the device RSA private key,
 * then the payload is decrypted using AES-256-GCM.
 */
internal class EnvelopeDecryptor(
    private val keyStoreManager: KeyStoreManager,
) : CredentialDecryptor {
    override fun decrypt(envelope: EncryptedEnvelope): ByteArray {
        val aesKey =
            decryptAesKey(
                encryptedKey = envelope.encryptedKey,
            )

        return decryptPayload(
            aesKey = aesKey,
            nonce = envelope.nonce,
            ciphertext = envelope.ciphertext,
        )
    }

    private fun decryptAesKey(encryptedKey: String): ByteArray {
        val privateKey =
            keyStoreManager.getPrivateKey()

        val cipher =
            Cipher.getInstance(
                RSA_OAEP_TRANSFORMATION,
            )

        val oaepParameters =
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT,
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            privateKey,
            oaepParameters,
        )

        return cipher.doFinal(
            Base64.decode(
                encryptedKey,
                Base64.NO_WRAP,
            ),
        )
    }

    private fun decryptPayload(
        aesKey: ByteArray,
        nonce: String,
        ciphertext: String,
    ): ByteArray {
        val cipher =
            Cipher.getInstance(
                AES_GCM_TRANSFORMATION,
            )

        val secretKey =
            SecretKeySpec(
                aesKey,
                AES_ALGORITHM,
            )

        val gcmParameterSpec =
            GCMParameterSpec(
                GCM_TAG_LENGTH_BITS,
                Base64.decode(
                    nonce,
                    Base64.NO_WRAP,
                ),
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            gcmParameterSpec,
        )

        return cipher.doFinal(
            Base64.decode(
                ciphertext,
                Base64.NO_WRAP,
            ),
        )
    }

    companion object {
        private const val RSA_OAEP_TRANSFORMATION =
            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        private const val AES_GCM_TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val AES_ALGORITHM =
            "AES"

        private const val GCM_TAG_LENGTH_BITS =
            128
    }
}
