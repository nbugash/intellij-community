// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.ClientOffer
import com.intellij.remoteDev.protocol.HandshakeAccepted
import com.intellij.remoteDev.protocol.HandshakeRefused
import com.intellij.remoteDev.protocol.ProtocolVersion
import com.intellij.remoteDev.protocol.ProtocolVersions
import com.intellij.remoteDev.protocol.SessionFailure
import com.intellij.remoteDev.protocol.SessionId
import com.intellij.remoteDev.protocol.SessionToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers contract section 3 and the ordered failure model in section 6.
 *
 * The order matters. A refusal must be decided before the host touches project state, so version and
 * credential checks come before the project is looked up at all.
 */
class HandshakeResponderTest {
  private val goodToken = SessionToken("valid")
  private val projectPath = "/srv/project"

  private class RecordingPolicy(
    private val validToken: SessionToken,
    private val availability: ProjectAvailability,
  ) : BackendSessionPolicy {
    var projectWasLookedUp: Boolean = false
      private set

    override fun isTokenValid(token: SessionToken): Boolean = token == validToken

    override fun projectAvailability(path: String): ProjectAvailability {
      projectWasLookedUp = true
      return availability
    }
  }

  private fun responder(policy: BackendSessionPolicy): HandshakeResponder =
    HandshakeResponder(
      policy = policy,
      supportedVersions = ProtocolVersions.supported(),
      backendProductVersion = "263.SNAPSHOT",
      nextSessionId = { SessionId("s-1") },
    )

  private fun offer(
    versions: List<ProtocolVersion> = ProtocolVersions.supported(),
    token: SessionToken = goodToken,
  ) = ClientOffer(versions, "263.SNAPSHOT", "linux-x86_64", token, projectPath)

  @Test
  fun `a good offer is accepted with the negotiated version and a session id`() {
    val reply = responder(RecordingPolicy(goodToken, ProjectAvailability.AVAILABLE)).respond(offer())

    val accepted = assertInstanceOf(HandshakeAccepted::class.java, reply)
    assertEquals(ProtocolVersions.CURRENT, accepted.negotiatedVersion)
    assertEquals(SessionId("s-1"), accepted.sessionId)
  }

  @Test
  fun `a version mismatch is refused before the project is looked up`() {
    // Contract section 3.3. The refusal must precede any project state being touched.
    val policy = RecordingPolicy(goodToken, ProjectAvailability.AVAILABLE)

    val reply = responder(policy).respond(offer(versions = listOf(ProtocolVersion(99))))

    assertEquals(SessionFailure.VERSION_MISMATCH, assertInstanceOf(HandshakeRefused::class.java, reply).reason)
    assertFalse(policy.projectWasLookedUp, "the host looked up the project despite refusing")
  }

  @Test
  fun `a bad credential is refused before the project is looked up`() {
    val policy = RecordingPolicy(goodToken, ProjectAvailability.AVAILABLE)

    val reply = responder(policy).respond(offer(token = SessionToken("wrong")))

    assertEquals(SessionFailure.AUTH_REJECTED, assertInstanceOf(HandshakeRefused::class.java, reply).reason)
    assertFalse(policy.projectWasLookedUp, "the host looked up the project despite refusing")
  }

  @Test
  fun `a missing project is refused`() {
    val reply = responder(RecordingPolicy(goodToken, ProjectAvailability.NOT_FOUND)).respond(offer())

    assertEquals(SessionFailure.PROJECT_NOT_FOUND, assertInstanceOf(HandshakeRefused::class.java, reply).reason)
  }

  @Test
  fun `a project already held by another session is refused`() {
    val reply = responder(RecordingPolicy(goodToken, ProjectAvailability.LOCKED)).respond(offer())

    assertEquals(SessionFailure.PROJECT_LOCKED, assertInstanceOf(HandshakeRefused::class.java, reply).reason)
  }

  @Test
  fun `a refusal states both version sets`() {
    val reply = responder(RecordingPolicy(goodToken, ProjectAvailability.AVAILABLE))
      .respond(offer(versions = listOf(ProtocolVersion(99))))

    val refused = assertInstanceOf(HandshakeRefused::class.java, reply)
    assertEquals(listOf(ProtocolVersion(99)), refused.offeredVersions)
    assertTrue(refused.backendSupportedVersions.isNotEmpty())
  }

  @Test
  fun `a refusal never carries the offered token`() {
    // FR-008. A refusal is logged, so it must not echo a credential.
    val reply = responder(RecordingPolicy(goodToken, ProjectAvailability.AVAILABLE))
      .respond(offer(token = SessionToken("super-secret-value")))

    assertFalse(reply.toString().contains("super-secret-value"))
  }
}
