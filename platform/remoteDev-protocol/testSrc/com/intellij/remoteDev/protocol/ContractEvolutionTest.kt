// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract section 7. The rules a change to the wire contract must respect.
 *
 * These tests exist so that a future change that breaks an older peer fails the build rather than a
 * user's session.
 */
class ContractEvolutionTest {
  /** The codes protocol version 1 puts on the wire. See contract section 6. */
  private val WIRE_CODES_V1 = setOf(
    "VERSION_MISMATCH", "PRODUCT_MISMATCH", "AUTH_REJECTED", "PROJECT_NOT_FOUND",
    "PROJECT_LOCKED", "BACKEND_NOT_READY", "SESSION_EXPIRED", "TRUST_REQUIRED",
  )

  private val lenient = Json { ignoreUnknownKeys = true }

  @Test
  fun `a peer ignores a field it does not know`() {
    // Rule 1. A new field must be optional, so an older peer can ignore it.
    val withFutureField = """
      {"reason":"AUTH_REJECTED","offeredVersions":[],"backendSupportedVersions":[1],"futureField":"whatever"}
    """.trimIndent()

    val restored = lenient.decodeFromString(HandshakeRefused.serializer(), withFutureField)

    assertEquals(SessionFailure.AUTH_REJECTED, restored.reason)
  }

  @Test
  fun `every failure code keeps a stable wire name`() {
    // Rule 4. Removing or renaming a code is a breaking change and needs a new protocol version.
    assertTrue(SessionFailure.entries.map { it.name }.containsAll(WIRE_CODES_V1))
  }

  @Test
  fun `a code added after version 1 is never sent to a peer`() {
    // Adding a code is safe only while no peer can receive it. An older peer cannot deserialize a
    // name it does not know, so a code that travels needs a new protocol version.
    //
    // INSECURE_TRANSPORT does not travel. The client decides it locally, before the handshake, and
    // the backend binds SessionTransportSecurity.BIND_ADDRESS, so a routable plain text connection
    // never reaches the backend to be refused.
    //
    // This test fails for the next code somebody adds. That is the point: it asks whether the new
    // code goes on the wire, and makes the answer part of the contract.
    val addedAfterVersion1 = SessionFailure.entries.map { it.name }.toSet() - WIRE_CODES_V1

    assertEquals(setOf("INSECURE_TRANSPORT"), addedAfterVersion1)
  }

  @Test
  fun `the current protocol version is advertised`() {
    // Rule 3. When a new version ships the previous one must still be supported.
    assertTrue(ProtocolVersions.supported().contains(ProtocolVersions.CURRENT))
  }

  @Test
  fun `a reply keeps its polymorphic discriminator`() {
    // A sealed reply crosses the wire by type name. Renaming a subtype breaks an older peer.
    val encoded = lenient.encodeToString(
      HandshakeReply.serializer(),
      HandshakeAccepted(ProtocolVersions.CURRENT, "263.SNAPSHOT", SessionId("s"), emptySet()),
    )

    assertTrue(encoded.contains("HandshakeAccepted"), "the discriminator changed: $encoded")
  }
}
