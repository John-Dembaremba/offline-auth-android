package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.crypto.EnvelopeDecryptor
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.serialization.CredentialParser

/**
 * Decrypts and parses synchronized credential envelopes.
 */
class DefaultCredentialEnvelopeProcessor(
    private val envelopeDecryptor: EnvelopeDecryptor,
    private val credentialParser: CredentialParser,
) : CredentialEnvelopeProcessor {
    override fun process(envelope: EncryptedEnvelope): OfflineCredential {
        val decryptedPayload =
            envelopeDecryptor.decrypt(
                envelope = envelope,
            )

        return credentialParser.parse(
            payload = decryptedPayload,
        )
    }
}
