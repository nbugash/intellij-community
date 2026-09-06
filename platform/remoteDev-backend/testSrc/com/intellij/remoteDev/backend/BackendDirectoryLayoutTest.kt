// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * FR-019 requires several backends to run on one host at the same time without interference.
 * They interfere if they share a configuration directory or a system directory.
 */
class BackendDirectoryLayoutTest {
  private val root: Path = Path.of("/srv/backends")

  @Test
  fun `two backends never share a system directory`() {
    assertNotEquals(
      BackendDirectoryLayout.systemDir(root, BackendId("alpha")),
      BackendDirectoryLayout.systemDir(root, BackendId("beta")),
    )
  }

  @Test
  fun `two backends never share a config directory`() {
    assertNotEquals(
      BackendDirectoryLayout.configDir(root, BackendId("alpha")),
      BackendDirectoryLayout.configDir(root, BackendId("beta")),
    )
  }

  @Test
  fun `one backend never mixes its config and system directories`() {
    val id = BackendId("alpha")

    assertNotEquals(BackendDirectoryLayout.configDir(root, id), BackendDirectoryLayout.systemDir(root, id))
  }

  @Test
  fun `the layout is stable, so a restart reuses the same directories`() {
    val id = BackendId("alpha")

    assertEquals(BackendDirectoryLayout.systemDir(root, id), BackendDirectoryLayout.systemDir(root, id))
  }

  @Test
  fun `every directory stays under the root`() {
    val id = BackendId("alpha")

    assertTrue(BackendDirectoryLayout.systemDir(root, id).startsWith(root))
    assertTrue(BackendDirectoryLayout.configDir(root, id).startsWith(root))
  }

  @Test
  fun `an identifier that could escape the root is refused`() {
    // A traversal in the identifier would place a directory outside the root.
    listOf("..", "../alpha", "alpha/beta", "alpha\\beta", "", " ").forEach { candidate ->
      assertThrows(IllegalArgumentException::class.java, { BackendId(candidate) }, "accepted '$candidate'")
    }
  }

  @Test
  fun `an ordinary identifier is accepted`() {
    assertEquals("alpha-1_2", BackendId("alpha-1_2").value)
  }
}
