// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-008 and SC-013. A credential never reaches a log or a diagnostic report.
 *
 * The types redact themselves, which covers the ordinary path. This covers the path where a raw
 * value reaches a log some other way, such as through a message a peer sent.
 */
class SecretRedactionTest {
  private val secret = "s3cr3t-token-value-abcdefghijklmnop"

  @Test
  fun `a known token value is masked`() {
    val text = LogRedaction.redact("connecting with token=$secret", listOf(secret))

    assertFalse(text.contains(secret))
    assertTrue(text.contains(LogRedaction.MASK))
  }

  @Test
  fun `every occurrence is masked, not only the first`() {
    val text = LogRedaction.redact("$secret and again $secret", listOf(secret))

    assertFalse(text.contains(secret))
    assertEquals(2, LogRedaction.MASK.toRegex().findAll(text).count())
  }

  @Test
  fun `text around the secret survives`() {
    val text = LogRedaction.redact("before $secret after", listOf(secret))

    assertTrue(text.startsWith("before "))
    assertTrue(text.endsWith(" after"))
  }

  @Test
  fun `a blank secret does not mask the whole message`() {
    // A cleared credential must not turn every log line into a row of masks.
    val text = LogRedaction.redact("nothing secret here", listOf("", "   "))

    assertEquals("nothing secret here", text)
  }

  @Test
  fun `a very short secret is not masked`() {
    // Masking a two character value would redact ordinary words and make the log useless.
    val text = LogRedaction.redact("the id is ab", listOf("ab"))

    assertEquals("the id is ab", text)
  }

  @Test
  fun `a regex character in a secret is treated as text`() {
    // A credential containing a dot or a bracket must not be compiled as a pattern.
    val awkward = "a.b[c]*d+efghijklmnop"
    val text = LogRedaction.redact("value=$awkward", listOf(awkward))

    assertFalse(text.contains(awkward))
    assertTrue(text.contains(LogRedaction.MASK))
  }

  @Test
  fun `nothing to redact leaves the text alone`() {
    assertEquals("clean line", LogRedaction.redact("clean line", emptyList()))
  }

  // The types that carry a credential hide it themselves. This is the first line: it holds even for
  // a log statement that nobody wrote with redaction in mind, such as string interpolation.

  @Test
  fun `a session token does not disclose its value`() {
    val token = SessionToken(secret)

    assertFalse(token.toString().contains(secret))
    assertFalse("presenting $token".contains(secret))
  }

  @Test
  fun `a session token still yields its value to code that asks`() {
    // Redaction must not break the one caller that needs the value: the code that presents it.
    assertEquals(secret, SessionToken(secret).value)
  }
}
