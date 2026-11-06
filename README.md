# offline-auth-android

Secure offline authentication for Android using Android Keystore, encrypted credential storage, and local password verification.

`offline-auth-android` is a backend-agnostic Kotlin library that allows Android applications to authenticate users while offline without storing plaintext passwords.

The device generates an RSA key pair in Android Keystore, sends only the public key to the backend, receives encrypted offline credentials, stores them securely, and later verifies passwords locally.

## Features

- Android Keystore RSA key management
- RSA-OAEP-SHA256 key unwrapping
- AES-256-GCM credential encryption
- Encrypted local credential storage
- PBKDF2-SHA256 password verification
- Multiple offline users per device
- Credential expiration validation
- Credential version rollback protection
- Authoritative snapshot synchronization
- Partial-success synchronization
- Typed synchronization failures
- Adaptive concurrent credential processing
- Backend-agnostic protocol

## Requirements

- Android API 23+
- Java 21
- Kotlin
- Android Gradle Plugin 9+

## Basic Usage

Create the manager:

```kotlin
val offlineAuth =
    OfflineAuthManager.create(context)
```

Get the device public key:

```kotlin
val publicKey =
    offlineAuth.getPublicKey()
```

Send the public key to your backend when requesting offline credentials.

Parse the backend response:

```kotlin
val snapshot =
    SnapshotParser().parse(responseBody)
```

Synchronize credentials:

```kotlin
val result =
    offlineAuth.sync(
        snapshot = snapshot,
    )
```

Authenticate while offline:

```kotlin
val authenticated =
    offlineAuth.authenticate(
        username = "alice",
        password = password.toCharArray(),
    )
```

## Backend Snapshot

The backend should return an authoritative snapshot similar to:

```json
{
  "protocol_version": 1,
  "credentials": [
    {
      "user_id": "1",
      "username": "alice",
      "envelope": {
        "key_algorithm": "RSA-OAEP-256",
        "content_algorithm": "A256GCM",
        "encrypted_key": "...",
        "nonce": "...",
        "ciphertext": "..."
      }
    }
  ],
  "failures": [
    {
      "user_id": "2",
      "username": "bob",
      "reason": "unusable_password"
    }
  ]
}
```

Users appearing in either `credentials` or `failures` are considered present in the authoritative snapshot.

## Supported Protocol

Current protocol:

```text
protocol_version = 1
key_algorithm = RSA-OAEP-256
content_algorithm = A256GCM
password_algorithm = pbkdf2_sha256
```

Unsupported protocol versions or algorithms are rejected before credentials are stored.

## Security Model

The Android device generates an RSA key pair inside Android Keystore.

The private key never leaves the device.

The synchronization flow is:

```text
Android device
    ↓
Generate / reuse RSA key pair
    ↓
Send public key to backend
    ↓
Backend encrypts credential with AES-256-GCM
    ↓
Backend wraps AES key with RSA public key
    ↓
Android decrypts and validates credential
    ↓
Credential stored encrypted locally
```

The backend does not need to permanently register the device or store its public key.

## Synchronization Rules

Successful credential:

```text
present + successfully processed
→ replace local credential
```

Backend failure:

```text
present in backend failures
→ preserve previous local credential
```

Missing user:

```text
missing from credentials and failures
→ remove local credential
```

A single failed credential does not fail the whole synchronization operation.

Example:

```text
alice → success
bob   → backend failure
mary  → success
peter → decryption failure
```

Alice and Mary are still synchronized successfully.

## Credential Version

`credential_version` represents the generation of a user's offline-auth state.

It is not simply the user's password version.

A normal password change may keep the same credential version while replacing the stored password verifier.

Version rules are:

```text
incoming < stored
→ reject as rollback

incoming == stored
→ replacement allowed

incoming > stored
→ replacement allowed
```

Example:

```text
stored = 5
incoming = 3
→ keep version 5
→ report credential_version_rollback
```

## Sync Results

`sync()` returns a `CredentialSyncResult` containing:

```text
successfulUsernames
failures
```

Failures are divided into:

```text
Local
Backend
```

Local failures use stable typed values such as:

```text
unsupported_key_algorithm
unsupported_content_algorithm
decryption_failed
invalid_credential_payload
credential_validation_failed
unsupported_password_algorithm
credential_version_rollback
credential_processing_failed
```

Backend failures preserve the reason returned by the backend.

Example handling:

```kotlin
result.failures.forEach { failure ->
    when (val reason = failure.reason) {
        is CredentialSyncFailureReason.Local -> {
            when (reason.reason) {
                LocalCredentialSyncFailureReason.CREDENTIAL_VERSION_ROLLBACK -> {
                    // Handle rollback.
                }

                LocalCredentialSyncFailureReason.DECRYPTION_FAILED -> {
                    // Handle local decryption failure.
                }

                else -> Unit
            }
        }

        is CredentialSyncFailureReason.Backend -> {
            println(reason.reason)
        }
    }
}
```

Rollback failures also expose:

```text
storedCredentialVersion
incomingCredentialVersion
```

## Credential Validation

Before an incoming credential is stored, the library validates:

- credential version
- issued timestamp
- expiration timestamp
- credential expiry
- supported password algorithm

Expired or invalid credentials are not stored.

## Multiple Users

Multiple offline users can be stored on the same device.

Each username has its own encrypted local credential.

## Concurrency

Credential synchronization uses bounded Kotlin coroutine parallelism.

Current strategy:

```text
0–9 credentials   → 1 worker
10–19 credentials → 2 workers
20+ credentials   → up to 4 workers
```

Actual parallelism is also limited by available processors.

## Public API

Primary entry point:

```kotlin
OfflineAuthManager
```

Main operations:

```kotlin
OfflineAuthManager.create(context)

offlineAuth.getPublicKey()

offlineAuth.sync(snapshot)

offlineAuth.authenticate(
    username,
    password,
)
```

Public models include:

```text
OfflineCredentialSnapshot
SynchronizedCredentialPayload
CredentialFailurePayload
EncryptedEnvelope

CredentialSyncResult
CredentialSyncFailure
CredentialSyncFailureReason
LocalCredentialSyncFailureReason
```

Crypto, storage, validation, password verification, and synchronization implementation classes are kept internal.

## Backend Independence

The library is not tied to Django.

Any backend can support the protocol as long as it can:

- accept an RSA public key
- produce protocol version 1 snapshots
- encrypt credential payloads with AES-256-GCM
- wrap the AES key using RSA-OAEP-SHA256
- return the expected snapshot structure

A companion Django package, `django-offline-auth`, can issue compatible credential snapshots.

## Testing

Run all local checks:

```bash
make
```

Useful commands:

```bash
make format
make lint
make test
make android-test-compile
make build
make precommit
```

Android Keystore tests require a real Android device or emulator.

## Security Notes

Applications using this library should:

- use HTTPS for backend communication
- never log plaintext passwords
- never log decrypted credentials or password verifiers
- keep offline credential lifetimes reasonably short
- treat backend snapshots as authoritative
- protect application backups appropriately
- never export Android Keystore private keys

## License

See the `LICENSE` file.
