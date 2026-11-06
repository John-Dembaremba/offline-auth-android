package io.github.johndembaremba.offlineauth.sync

/**
 * Thrown when the backend snapshot uses an unsupported protocol version.
 */
class UnsupportedProtocolVersionException(
    val protocolVersion: Int,
) : IllegalArgumentException(
        "Unsupported offline credential protocol version: $protocolVersion",
    )
