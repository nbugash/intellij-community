// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * FR-015 and SC-003. A session survives an outage of up to five minutes and loses no unsaved edit.
 *
 * SC-003 asks for 100% across at least 100 induced interruptions, so the last test drives that many
 * rather than a token few.
 */
class ReconnectionTest {
  private val id = SessionId("s-1")

  @Test
  fun `an outage inside the window can resume`() {
    val retention = SessionRetention(RetentionWindow.DEFAULT)
    retention.disconnected(id, at = 0.seconds)

    assertTrue(retention.canResume(id, now = 4.minutes))
  }

  @Test
  fun `an outage past the window cannot resume`() {
    val retention = SessionRetention(RetentionWindow.DEFAULT)
    retention.disconnected(id, at = 0.seconds)

    assertFalse(retention.canResume(id, now = 5.minutes + 1.seconds))
  }

  @Test
  fun `the default window is the five minutes the contract states`() {
    assertEquals(5.minutes, RetentionWindow.DEFAULT.duration)
  }

  @Test
  fun `resuming keeps the same session id`() {
    // Contract section 4. A reconnection resumes a session, it does not start one.
    val retention = SessionRetention(RetentionWindow.DEFAULT)
    retention.disconnected(id, at = 0.seconds)

    assertEquals(id, retention.resume(id, now = 1.minutes)?.sessionId)
  }

  @Test
  fun `an expired session reports what was lost rather than discarding it`() {
    // FR-015. Silent loss is the failure mode this guards against.
    val retention = SessionRetention(RetentionWindow.DEFAULT)
    retention.disconnected(id, at = 0.seconds)
    retention.pending(id).record(PendingEdit("A.kt", "fun a() {}"))
    retention.pending(id).record(PendingEdit("B.kt", "fun b() {}"))

    val expiry = retention.expire(now = 6.minutes).single()

    assertEquals(id, expiry.sessionId)
    assertEquals(2, expiry.lostEdits.size)
    assertTrue(expiry.lostEdits.any { it.path == "A.kt" }, "lost: ${expiry.lostEdits}")
  }

  @Test
  fun `edits made while disconnected are replayed on resume`() {
    val retention = SessionRetention(RetentionWindow.DEFAULT)
    retention.disconnected(id, at = 0.seconds)
    retention.pending(id).record(PendingEdit("A.kt", "one"))
    retention.pending(id).record(PendingEdit("A.kt", "two"))

    val resumed = retention.resume(id, now = 30.seconds)

    assertEquals(listOf("one", "two"), resumed!!.replay.map { it.text })
  }

  @Test
  fun `a resumed session starts with an empty buffer`() {
    // A replayed edit must not be replayed twice on the next outage.
    val retention = SessionRetention(RetentionWindow.DEFAULT)
    retention.disconnected(id, at = 0.seconds)
    retention.pending(id).record(PendingEdit("A.kt", "one"))
    retention.resume(id, now = 10.seconds)

    retention.disconnected(id, at = 20.seconds)

    assertEquals(0, retention.pending(id).size)
  }

  @Test
  fun `no edit is lost across a hundred induced interruptions`() {
    // SC-003 asks for 100% over at least 100 interruptions. Each round drops the connection at a
    // random moment inside the window, writes a few edits, then reconnects.
    val retention = SessionRetention(RetentionWindow.DEFAULT)
    val random = Random(seed = 20260904)
    val written = mutableListOf<String>()
    val replayed = mutableListOf<String>()
    var clock = 0.seconds

    repeat(100) { round ->
      retention.disconnected(id, at = clock)
      val edits = 1 + random.nextInt(4)
      repeat(edits) { n ->
        val text = "round-$round-edit-$n"
        written += text
        retention.pending(id).record(PendingEdit("A.kt", text))
      }
      clock += (1 + random.nextInt(4 * 60 * 1000)).milliseconds
      val resumed = retention.resume(id, now = clock)
      assertTrue(resumed != null, "round $round failed to resume inside the window")
      replayed += resumed!!.replay.map { it.text }
      clock += 1.seconds
    }

    assertEquals(written.size, replayed.size, "an edit was lost")
    assertEquals(written, replayed, "an edit was replayed out of order")
  }
}
