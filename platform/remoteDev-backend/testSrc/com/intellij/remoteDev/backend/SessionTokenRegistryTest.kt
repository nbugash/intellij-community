// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.SessionId
import com.intellij.remoteDev.protocol.SessionToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * FR-018. A session credential is revocable by the host owner.
 * FR-008. A credential never rests where it can be read back.
 */
class SessionTokenRegistryTest {
  private val session = SessionId("s-1")
  private val other = SessionId("s-2")

  @Test
  fun `an issued token validates to its session`() {
    val registry = SessionTokenRegistry()
    val token = registry.issue(session, ttl = 1.hours, now = 0.minutes)

    assertEquals(session, registry.validate(token, now = 5.minutes))
  }

  @Test
  fun `a token nobody issued does not validate`() {
    assertNull(SessionTokenRegistry().validate(SessionToken("invented"), now = 0.minutes))
  }

  @Test
  fun `a revoked token stops validating at once`() {
    // FR-018. Revocation is the host owner's control over a session, so it cannot wait for an expiry.
    val registry = SessionTokenRegistry()
    val token = registry.issue(session, 1.hours, now = 0.minutes)

    registry.revoke(session)

    assertNull(registry.validate(token, now = 1.minutes))
  }

  @Test
  fun `revoking one session leaves another alone`() {
    val registry = SessionTokenRegistry()
    val mine = registry.issue(session, 1.hours, now = 0.minutes)
    val theirs = registry.issue(other, 1.hours, now = 0.minutes)

    registry.revoke(session)

    assertNull(registry.validate(mine, now = 1.minutes))
    assertEquals(other, registry.validate(theirs, now = 1.minutes))
  }

  @Test
  fun `a token past its lifetime does not validate`() {
    val registry = SessionTokenRegistry()
    val token = registry.issue(session, ttl = 30.minutes, now = 0.minutes)

    assertNull(registry.validate(token, now = 31.minutes))
  }

  @Test
  fun `two issued tokens differ`() {
    // A predictable token is a token an attacker can guess.
    val registry = SessionTokenRegistry()

    assertNotEquals(
      registry.issue(session, 1.hours, 0.minutes).value,
      registry.issue(other, 1.hours, 0.minutes).value,
    )
  }

  @Test
  fun `the registry never holds the token it issued`() {
    // FR-008. If the registry leaks, the tokens in it must not be usable. It keeps a digest.
    val registry = SessionTokenRegistry()
    val token = registry.issue(session, 1.hours, 0.minutes)

    assertFalse(registry.debugContents().contains(token.value), "the registry stored the raw token")
    assertTrue(registry.debugContents().isNotEmpty(), "the registry stored nothing at all")
  }

  @Test
  fun `issuing again for a session replaces the previous token`() {
    // A reissue is how a compromised token is rotated, so the old one must stop working.
    val registry = SessionTokenRegistry()
    val first = registry.issue(session, 1.hours, 0.minutes)
    val second = registry.issue(session, 1.hours, 0.minutes)

    assertNull(registry.validate(first, 1.minutes))
    assertEquals(session, registry.validate(second, 1.minutes))
  }
}
