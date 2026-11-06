package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredential

/**
 * Converts an encrypted credential envelope into an offline credential.
 */
internal fun interface CredentialEnvelopeProcessor {
    fun process(envelope: EncryptedEnvelope): OfflineCredential
}
