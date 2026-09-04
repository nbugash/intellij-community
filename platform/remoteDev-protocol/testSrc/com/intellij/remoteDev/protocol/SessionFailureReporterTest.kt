// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-010. Every failure states the cause and one next action.
 * FR-008. No failure text carries a credential.
 */
class SessionFailureReporterTest {
  private val secret = "super-secret-value"

  private fun offer() = ClientOffer(
    supportedProtocolVersions = listOf(ProtocolVersion(9)),
    clientProductVersion = "999.SNAPSHOT",
    clientPlatform = "linux-x86_64",
    sessionToken = SessionToken(secret),
    requestedProjectPath = "/srv/project",
  )

  private fun refusal(reason: SessionFailure) =
    HandshakeRefused(reason, offer().supportedProtocolVersions, ProtocolVersions.supported())

  @Test
  fun `every failure code produces text`() {
    SessionFailure.entries.forEach { reason ->
      val text = SessionFailureReporter.describe(refusal(reason), offer(), "263.SNAPSHOT")
      assertTrue(text.isNotBlank(), "$reason produced no text")
    }
  }

  @Test
  fun `no failure text carries the session token`() {
    SessionFailure.entries.forEach { reason ->
      val text = SessionFailureReporter.describe(refusal(reason), offer(), "263.SNAPSHOT")
      assertFalse(text.contains(secret), "$reason leaked the token")
    }
  }

  @Test
  fun `a version mismatch names both sides`() {
    val text = SessionFailureReporter.describe(refusal(SessionFailure.VERSION_MISMATCH), offer(), "263.SNAPSHOT")

    assertTrue(text.contains("9"), "the client versions are missing: $text")
    assertTrue(text.contains(ProtocolVersions.CURRENT.toString()), "the host versions are missing: $text")
  }

  @Test
  fun `a missing project names the path the client asked for`() {
    val text = SessionFailureReporter.describe(refusal(SessionFailure.PROJECT_NOT_FOUND), offer(), "263.SNAPSHOT")

    assertTrue(text.contains("/srv/project"), "the path is missing: $text")
  }

  @Test
  fun `a product mismatch names both builds`() {
    val text = SessionFailureReporter.describe(refusal(SessionFailure.PRODUCT_MISMATCH), offer(), "263.SNAPSHOT")

    assertTrue(text.contains("999.SNAPSHOT"), "the client build is missing: $text")
    assertTrue(text.contains("263.SNAPSHOT"), "the host build is missing: $text")
  }
}
