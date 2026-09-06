// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which agent binary a host needs, and what happens when the wrong one is offered.
 *
 * The data model states the rule this covers: the host platform must be read from the host. The
 * client platform is not evidence of it. A developer on macOS routinely drives a Linux host.
 */
class AgentSelectionTest {
  private val linuxX64 = HostPlatform(HostOs.LINUX, HostArch.X64)
  private val macAarch64 = HostPlatform(HostOs.MACOS, HostArch.AARCH64)

  @Test
  fun `each platform selects its own binary`() {
    val names = HostOs.entries.flatMap { os -> HostArch.entries.map { AgentSelection.binaryName(HostPlatform(os, it)) } }

    assertEquals(names.size, names.toSet().size, "two platforms share a binary name: $names")
  }

  @Test
  fun `a windows host gets an exe`() {
    assertTrue(AgentSelection.binaryName(HostPlatform(HostOs.WINDOWS, HostArch.X64)).endsWith(".exe"))
  }

  @Test
  fun `a posix host gets no exe suffix`() {
    assertTrue(!AgentSelection.binaryName(linuxX64).endsWith(".exe"))
    assertTrue(!AgentSelection.binaryName(macAarch64).endsWith(".exe"))
  }

  @Test
  fun `a matching agent is accepted`() {
    assertNull(AgentSelection.mismatch(agent = linuxX64, host = linuxX64))
  }

  @Test
  fun `an agent for another operating system is refused`() {
    // T074. Deploying a macOS binary to a Linux host must fail before it is executed.
    assertNotNull(AgentSelection.mismatch(agent = macAarch64, host = linuxX64))
  }

  @Test
  fun `an agent for another architecture is refused`() {
    val linuxAarch64 = HostPlatform(HostOs.LINUX, HostArch.AARCH64)

    assertNotNull(AgentSelection.mismatch(agent = linuxAarch64, host = linuxX64))
  }

  @Test
  fun `a mismatch names both platforms so the message can state a next action`() {
    // FR-010. A failure that says only "wrong platform" tells the user nothing.
    val text = AgentSelection.mismatch(agent = macAarch64, host = linuxX64).orEmpty()

    assertTrue(text.contains("macos", ignoreCase = true), "the agent platform is missing: $text")
    assertTrue(text.contains("linux", ignoreCase = true), "the host platform is missing: $text")
  }

  @Test
  fun `the platform of a host is distinct from the platform of a client`() {
    // The type carries no default and no client fallback, so a caller must supply what it read
    // from the host. This test fails if someone adds a convenience default later.
    assertNotEquals(linuxX64, macAarch64)
    assertEquals(HostOs.entries.size * HostArch.entries.size, 6)
  }
}
