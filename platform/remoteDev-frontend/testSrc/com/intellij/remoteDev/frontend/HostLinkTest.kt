// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.frontend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI

class HostLinkTest {
  @Test
  fun `a well formed link parses into its parts`() {
    val link = HostLink.parse(URI("splitclient://build-01.example:5990/srv/project"))

    assertEquals("build-01.example", link.host)
    assertEquals(5990, link.port)
    assertEquals("/srv/project", link.projectPath)
  }

  @Test
  fun `a link round trips`() {
    val original = HostLink("build-01.example", 5990, "/srv/project")

    assertEquals(original, HostLink.parse(original.toUri()))
  }

  @Test
  fun `a link from another product is refused`() {
    // FR-052. This client speaks only its own protocol and must not appear to accept another one.
    assertThrows(IllegalArgumentException::class.java) { HostLink.parse(URI("tcp://host:5990/srv/p")) }
  }

  @Test
  fun `a link without a port is refused`() {
    assertThrows(IllegalArgumentException::class.java) { HostLink.parse(URI("splitclient://host/srv/p")) }
  }

  @Test
  fun `a link without a project path is refused`() {
    assertThrows(IllegalArgumentException::class.java) { HostLink.parse(URI("splitclient://host:5990")) }
  }

  @Test
  fun `a link without a host is refused`() {
    assertThrows(IllegalArgumentException::class.java) { HostLink.parse(URI("splitclient:///srv/p")) }
  }
}
