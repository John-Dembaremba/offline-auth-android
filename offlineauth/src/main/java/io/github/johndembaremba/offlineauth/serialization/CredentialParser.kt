package io.github.johndembaremba.offlineauth.serialization

import io.github.johndembaremba.offlineauth.model.OfflineCredential
import kotlinx.serialization.json.Json

/**
 * Parses decrypted credential payloads into offline credential models.
 */
internal class CredentialParser(
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
        },
) {
    fun parse(payload: ByteArray): OfflineCredential =
        json.decodeFromString(
            payload.decodeToString(),
        )
}
