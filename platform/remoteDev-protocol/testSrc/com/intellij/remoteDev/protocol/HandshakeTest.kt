// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the handshake in `contracts/session-protocol.md` section 3, and the secret rules in FR-008.
 */
class HandshakeTest {
  private val json = Json { ignoreUnknownKeys = true }

  private fun offer(versions: List<ProtocolVersion> = ProtocolVersions.supported()): ClientOffer =
    ClientOffer(
      supportedProtocolVersions = versions,
      clientProductVersion = "263.SNAPSHOT",
      clientPlatform = "linux-x86_64",
      sessionToken = SessionToken("super-secret-value"),
      requestedProjectPath = "/home/user/project",
    )

  @Test
  fun `an offer survives a serialization round trip`() {
    val restored = json.decodeFromString(ClientOffer.serializer(), json.encodeToString(ClientOffer.serializer(), offer()))

    assertEquals(offer(), restored)
  }

  @Test
  fun `a session token never appears in its own string form`() {
    // FR-008 and SC-013. A token reaching a log through toString is a defect.
    val token = SessionToken("super-secret-value")

    assertFalse(token.toString().contains("super-secret-value"))
    assertTrue(token.toString().isNotEmpty())
  }

  @Test
  fun `an offer never leaks its token through toString`() {
    assertFalse(offer().toString().contains("super-secret-value"))
  }

  @Test
  fun `an accepted reply carries the negotiated version and a session id`() {
    val reply = HandshakeAccepted(
      negotiatedVersion = ProtocolVersion(1),
      backendProductVersion = "263.SNAPSHOT",
      sessionId = SessionId("s-1"),
      capabilities = emptySet(),
    )

    assertEquals(ProtocolVersion(1), reply.negotiatedVersion)
    assertEquals(SessionId("s-1"), reply.sessionId)
  }

  @Test
  fun `a refusal names the reason and both version sets`() {
    // Contract section 3.2. A refusal that hides either side is not actionable.
    val refusal = HandshakeRefused(
      reason = SessionFailure.VERSION_MISMATCH,
      offeredVersions = listOf(ProtocolVersion(9)),
      backendSupportedVersions = ProtocolVersions.supported(),
    )

    assertEquals(SessionFailure.VERSION_MISMATCH, refusal.reason)
    assertTrue(refusal.offeredVersions.isNotEmpty())
    assertTrue(refusal.backendSupportedVersions.isNotEmpty())
  }

  @Test
  fun `every failure code carries a message key and a terminal flag`() {
    // FR-010. A code with no message cannot state a next action.
    SessionFailure.entries.forEach { failure ->
      assertTrue(failure.messageKey.startsWith("session.failure."), "bad key for $failure")
    }
  }

  @Test
  fun `a reply round trips as its sealed type`() {
    val refusal: HandshakeReply = HandshakeRefused(
      reason = SessionFailure.AUTH_REJECTED,
      offeredVersions = emptyList(),
      backendSupportedVersions = ProtocolVersions.supported(),
    )
    val restored = json.decodeFromString(HandshakeReply.serializer(), json.encodeToString(HandshakeReply.serializer(), refusal))

    assertEquals(refusal, restored)
  }
}
