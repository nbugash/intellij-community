// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SC-013 and FR-008. No credential reaches a produced artifact. */
class SecretScannerTest {
  @Test
  fun `a private key block is found`() {
    val findings = SecretScanner.scan("-----BEGIN RSA PRIVATE KEY-----", "log.txt")

    assertEquals("private key", findings.single().kind)
  }

  @Test
  fun `a password assignment is found`() {
    assertEquals(1, SecretScanner.scan("db.password=hunter2", "app.properties").size)
  }

  @Test
  fun `a bearer token is found`() {
    assertEquals(1, SecretScanner.scan("Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345", "log").size)
  }

  @Test
  fun `the finding names the file and the line`() {
    val findings = SecretScanner.scan("clean\nclean\ndb.password=hunter2", "app.properties")

    assertEquals("app.properties", findings.single().source)
    assertEquals(3, findings.single().line)
  }

  @Test
  fun `a redacted token is not a finding`() {
    // This fork prints a fixed marker in place of a credential. Flagging that marker would punish
    // the behaviour FR-008 asks for, and would train people to switch the scanner off.
    assertTrue(SecretScanner.scan("token=SessionToken(redacted)", "log").isEmpty())
    assertTrue(SecretScanner.scan("ref=CredentialRef(hidden)", "log").isEmpty())
  }

  @Test
  fun `an empty password is not a finding`() {
    // A template or a cleared setting is not a leak.
    assertTrue(SecretScanner.scan("password=", "app.properties").isEmpty())
  }

  @Test
  fun `ordinary prose is not a finding`() {
    val text = "The user enters a password on the connection screen. See the password field."

    assertTrue(SecretScanner.scan(text, "doc.md").isEmpty(), "false positive on prose")
  }

  @Test
  fun `clean text yields nothing`() {
    assertTrue(SecretScanner.scan("nothing to see\nhere at all", "log").isEmpty())
  }
}
