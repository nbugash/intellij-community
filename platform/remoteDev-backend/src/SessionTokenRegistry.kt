// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.SessionId
import com.intellij.remoteDev.protocol.SessionToken
import com.intellij.openapi.components.Service
import org.jetbrains.annotations.ApiStatus
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Issues, validates and revokes the credential that authorises one session.
 *
 * Three properties matter, and each has a test.
 *
 * A token is unguessable. It comes from [SecureRandom], because a predictable credential is one an
 * attacker can produce without ever seeing a real one.
 *
 * The registry never holds the token it issued. It keeps a digest, so a registry that leaks yields
 * nothing a client could present. FR-008 requires a credential not to rest where it can be read
 * back.
 *
 * A token is revocable at once. FR-018 gives the host owner control over a session, and control
 * that waits for an expiry is not control.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class SessionTokenRegistry {
  private data class Issued(val session: SessionId, val digest: String, val expiresAt: Duration)

  private val bySession = ConcurrentHashMap<SessionId, Issued>()
  private val random = SecureRandom()

  /** Issues a token for [session], replacing any token it already had. */
  fun issue(session: SessionId, ttl: Duration, now: Duration): SessionToken {
    val raw = ByteArray(TOKEN_BYTES).also(random::nextBytes)
    val token = SessionToken(Base64.getUrlEncoder().withoutPadding().encodeToString(raw))
    bySession[session] = Issued(session, digest(token), now + ttl)
    return token
  }

  /** Returns the session [token] authorises, or null when it is unknown, revoked or expired. */
  fun validate(token: SessionToken, now: Duration): SessionId? {
    val candidate = digest(token)
    val issued = bySession.values.firstOrNull { constantTimeEquals(it.digest, candidate) } ?: return null
    if (now > issued.expiresAt) return null
    return issued.session
  }

  /** Ends the host owner's trust in this session's credential. */
  fun revoke(session: SessionId) {
    bySession.remove(session)
  }

  /** What the registry holds. Exposed so a test can assert no raw token is among it. */
  fun debugContents(): String = bySession.values.joinToString(",") { it.digest }

  private fun digest(token: SessionToken): String =
    Base64.getEncoder().encodeToString(MessageDigest.getInstance(DIGEST).digest(token.value.toByteArray()))

  /**
   * Compares without leaking where two values first differ. A comparison that returns early tells an
   * attacker how much of a guess was right, which turns guessing a token into guessing it one byte
   * at a time.
   */
  private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

  private companion object {
    const val TOKEN_BYTES: Int = 32
    const val DIGEST: String = "SHA-256"
  }
}
