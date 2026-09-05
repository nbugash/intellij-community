// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.PendingEdit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-015. The edits a client made while disconnected are applied when it comes back.
 *
 * These tests cover the replay policy. Writing into the platform's shared document model needs a
 * live kernel transaction, so that adapter is verified by compilation and by the end-to-end suite.
 */
class DocumentReplayTest {
  private class FakeDocuments(private val known: Set<String>) : SharedDocuments {
    val applied = mutableListOf<Pair<String, String>>()

    override fun apply(path: String, text: String): Boolean {
      if (path !in known) return false
      applied += path to text
      return true
    }
  }

  @Test
  fun `edits are applied in the order they were made`() {
    val docs = FakeDocuments(setOf("A.kt"))
    val edits = listOf(PendingEdit("A.kt", "one"), PendingEdit("A.kt", "two"), PendingEdit("A.kt", "three"))

    DocumentReplay.replay(edits, docs)

    assertEquals(listOf("one", "two", "three"), docs.applied.map { it.second })
  }

  @Test
  fun `a replay reports how many edits it applied`() {
    val report = DocumentReplay.replay(listOf(PendingEdit("A.kt", "x")), FakeDocuments(setOf("A.kt")))

    assertEquals(1, report.applied)
    assertTrue(report.rejected.isEmpty())
    assertTrue(report.isComplete)
  }

  @Test
  fun `an edit to a document the host cannot open is reported, not dropped`() {
    // FR-015 forbids silent loss. A rejected edit must reach the user, not vanish into a log.
    val report = DocumentReplay.replay(
      listOf(PendingEdit("A.kt", "kept"), PendingEdit("gone.kt", "lost")),
      FakeDocuments(setOf("A.kt")),
    )

    assertEquals(1, report.applied)
    assertEquals(1, report.rejected.size)
    assertEquals("gone.kt", report.rejected.single().path)
    assertFalse(report.isComplete)
  }

  @Test
  fun `a replay continues after a rejected edit`() {
    // Stopping at the first rejection would lose the edits behind it, which is the loss FR-015
    // forbids. Every edit is attempted and the failures are collected.
    val docs = FakeDocuments(setOf("A.kt"))
    val report = DocumentReplay.replay(
      listOf(PendingEdit("gone.kt", "a"), PendingEdit("A.kt", "b"), PendingEdit("gone.kt", "c")),
      docs,
    )

    assertEquals(listOf("b"), docs.applied.map { it.second })
    assertEquals(2, report.rejected.size)
  }

  @Test
  fun `replaying nothing is complete`() {
    val report = DocumentReplay.replay(emptyList(), FakeDocuments(emptySet()))

    assertEquals(0, report.applied)
    assertTrue(report.isComplete)
  }

  @Test
  fun `a report states its numbers`() {
    val report = DocumentReplay.replay(
      listOf(PendingEdit("A.kt", "x"), PendingEdit("gone.kt", "y")),
      FakeDocuments(setOf("A.kt")),
    )

    assertTrue(report.summary.contains("1"), "summary: ${report.summary}")
    assertTrue(report.summary.contains("gone.kt"), "summary: ${report.summary}")
  }
}
