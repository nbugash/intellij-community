// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Quoting for the remote shell.
 *
 * These run everywhere, with no host, because this is the part that is dangerous when it is wrong.
 * `ssh` joins its arguments and hands the result to the host's shell, so an unquoted metacharacter
 * runs there.
 */
class SshCommandQuotingTest {
  private fun quote(vararg parts: String) = SshHostBootstrap.quote(parts.toList())

  @Test
  fun `an ordinary command is quoted whole`() {
    assertEquals("'echo' 'hello'", quote("echo", "hello"))
  }

  @Test
  fun `a semicolon cannot start a second command`() {
    // Unquoted, this deletes a home directory on the host.
    val quoted = quote("echo", "a; rm -rf ~")

    assertEquals("'echo' 'a; rm -rf ~'", quoted)
    assertTrue(quoted.endsWith("'"), "The payload must stay inside the quotes")
  }

  @Test
  fun `a backtick does not substitute`() {
    assertEquals("'echo' '`whoami`'", quote("echo", "`whoami`"))
  }

  @Test
  fun `a dollar sign does not expand`() {
    assertEquals("'echo' '\$HOME'", quote("echo", "\$HOME"))
  }

  @Test
  fun `a single quote is closed escaped and reopened`() {
    // The only sequence that survives: '\'' ends the quote, escapes one, and starts a new quote.
    assertEquals("'it'\\''s'", quote("it's"))
  }

  @Test
  fun `a quote cannot be used to break out`() {
    // The classic escape: close the quote, run something, reopen. It must not survive.
    val quoted = quote("'; rm -rf ~; echo '")

    assertTrue(quoted.startsWith("'") && quoted.endsWith("'"))
    assertEquals("''\\''; rm -rf ~; echo '\\'''", quoted)
  }

  @Test
  fun `an empty argument stays an argument`() {
    // Dropping it would shift every later argument by one position.
    assertEquals("'a' '' 'b'", quote("a", "", "b"))
  }

  @Test
  fun `a newline stays inside the argument`() {
    assertEquals("'a\nb'", quote("a\nb"))
  }
}
