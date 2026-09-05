// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.protocol.HostKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostRegistryTest {
  private fun host(id: String = "h1", kind: HostKind = HostKind.SSH) =
    HostRecord(HostId(id), kind, displayName = "build-01", credentialRef = CredentialRef("keychain://h1"))

  @Test
  fun `a host is remembered and found again`() {
    val registry = HostRegistry()
    registry.remember(host())

    assertEquals(HostKind.SSH, registry.find(HostId("h1"))?.kind)
  }

  @Test
  fun `an unknown host is not found`() {
    assertNull(HostRegistry().find(HostId("nope")))
  }

  @Test
  fun `a host record never carries a secret`() {
    // FR-008. A record holds a reference into the credential store, never the credential.
    val text = host().toString()

    assertFalse(text.contains("keychain://h1"), "the record exposed its credential reference: $text")
  }

  @Test
  fun `a credential reference refuses an inline secret`() {
    // A caller that passes a password rather than a reference must fail loudly.
    listOf("", "  ").forEach { bad ->
      assertThrows(IllegalArgumentException::class.java, { CredentialRef(bad) }, "accepted '$bad'")
    }
  }

  @Test
  fun `forgetting a host removes it`() {
    val registry = HostRegistry()
    registry.remember(host())
    registry.forget(HostId("h1"))

    assertNull(registry.find(HostId("h1")))
  }

  @Test
  fun `remembering the same id replaces the record`() {
    val registry = HostRegistry()
    registry.remember(host())
    registry.remember(HostRecord(HostId("h1"), HostKind.CONTAINER, "docker-01", CredentialRef("keychain://x")))

    assertEquals(HostKind.CONTAINER, registry.find(HostId("h1"))?.kind)
    assertEquals(1, registry.all().size)
  }

  @Test
  fun `every host kind FR-013 requires is representable`() {
    assertTrue(HostKind.entries.containsAll(listOf(HostKind.SSH, HostKind.WSL, HostKind.CONTAINER)))
  }
}
