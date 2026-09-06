// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the version rule in `contracts/session-protocol.md` section 3.3, and SC-018.
 */
class VersionNegotiationTest {
  @Test
  fun `selects the first offered version that the host supports`() {
    val offered = listOf(ProtocolVersion(3), ProtocolVersion(2), ProtocolVersion(1))
    val supported = listOf(ProtocolVersion(2), ProtocolVersion(1))

    assertEquals(ProtocolVersion(2), VersionNegotiator.select(offered, supported))
  }

  @Test
  fun `follows the client's order of preference, not the host's`() {
    val offered = listOf(ProtocolVersion(2), ProtocolVersion(1))
    val supported = listOf(ProtocolVersion(1), ProtocolVersion(2))

    assertEquals(ProtocolVersion(2), VersionNegotiator.select(offered, supported))
  }

  @Test
  fun `returns nothing when no version is shared`() {
    val offered = listOf(ProtocolVersion(9))
    val supported = listOf(ProtocolVersion(1))

    assertNull(VersionNegotiator.select(offered, supported))
  }

  @Test
  fun `returns nothing when the client offers no version`() {
    assertNull(VersionNegotiator.select(emptyList(), ProtocolVersions.supported()))
  }

  @Test
  fun `a host advertises the two most recent versions once two exist`() {
    // FR-057. With only one version in existence the list holds one entry.
    val supported = ProtocolVersions.supportedBy(ProtocolVersion(4))

    assertEquals(listOf(ProtocolVersion(4), ProtocolVersion(3)), supported)
  }

  @Test
  fun `a host at the earliest version advertises only that version`() {
    assertEquals(listOf(ProtocolVersion(1)), ProtocolVersions.supportedBy(ProtocolVersion(1)))
  }

  @Test
  fun `versions order by number`() {
    assertTrue(ProtocolVersion(1) < ProtocolVersion(2))
    assertEquals(listOf(ProtocolVersion(1), ProtocolVersion(2)), listOf(ProtocolVersion(2), ProtocolVersion(1)).sorted())
  }
}
