package io.github.johndembaremba.offlineauth.serialization

import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import kotlinx.serialization.json.Json

/**
 * Parses backend snapshot JSON into offline credential snapshot models.
 */
class SnapshotParser(
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
        },
) {
    fun parse(payload: String): OfflineCredentialSnapshot = json.decodeFromString(payload)
}
