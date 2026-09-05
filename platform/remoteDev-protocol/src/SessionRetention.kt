// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How long a host holds a session whose connection dropped.
 *
 * Contract section 4 states "at least five minutes". The exact value is configurable, because a host
 * owner may want longer on a poor link, and shorter where a held session costs memory.
 */
@ApiStatus.Internal
@Serializable
@JvmInline
value class RetentionWindow(val duration: Duration) {
  init {
    // Validate against the constant, never against DEFAULT. Reading DEFAULT here would construct a
    // RetentionWindow, which would run this check again, and recurse until the stack ran out.
    require(duration >= MINIMUM) { "The contract requires at least $MINIMUM, got $duration" }
  }

  companion object {
    /** Contract section 4 states "at least five minutes". */
    val MINIMUM: Duration = 5.minutes

    val DEFAULT: RetentionWindow = RetentionWindow(MINIMUM)
  }
}

/** One edit a client made while its connection was down. */
@ApiStatus.Internal
@Serializable
data class PendingEdit(val path: String, val text: String)

/**
 * The edits a client made while disconnected, in the order it made them.
 *
 * Order matters. Replaying two edits to one file in the wrong order produces different content, so
 * this is a log rather than a set.
 */
@ApiStatus.Internal
class PendingEditLog {
  private val edits = ArrayDeque<PendingEdit>()

  val size: Int get() = synchronized(edits) { edits.size }

  fun record(edit: PendingEdit) {
    synchronized(edits) { edits.addLast(edit) }
  }

  /** Returns every edit and empties the log, so nothing is replayed twice. */
  fun drain(): List<PendingEdit> = synchronized(edits) {
    val drained = edits.toList()
    edits.clear()
    drained
  }
}

/** A session that came back inside the window, and the edits to replay into it. */
@ApiStatus.Internal
data class ResumedSession(val sessionId: SessionId, val replay: List<PendingEdit>)

/** A session that stayed down past the window, and the work that could not be replayed. */
@ApiStatus.Internal
data class ExpiredSession(val sessionId: SessionId, val lostEdits: List<PendingEdit>)

/**
 * Holds a session whose connection dropped, so that a reconnection resumes rather than restarts.
 *
 * FR-015 requires an outage of up to five minutes to lose no unsaved edit, and requires an expiry to
 * report what was lost rather than discard it silently. [expire] therefore returns the pending work,
 * and a caller is expected to put it somewhere the user can recover it.
 */
@ApiStatus.Internal
class SessionRetention(private val window: RetentionWindow) {
  private data class Held(val since: Duration, val edits: PendingEditLog)

  private val held = ConcurrentHashMap<SessionId, Held>()

  fun disconnected(sessionId: SessionId, at: Duration) {
    held.compute(sessionId) { _, existing -> Held(at, existing?.edits ?: PendingEditLog()) }
  }

  /** The log a disconnected client's edits accumulate in. */
  fun pending(sessionId: SessionId): PendingEditLog =
    held.computeIfAbsent(sessionId) { Held(Duration.ZERO, PendingEditLog()) }.edits

  fun canResume(sessionId: SessionId, now: Duration): Boolean {
    val entry = held[sessionId] ?: return false
    return now - entry.since <= window.duration
  }

  /** Resumes the session and hands back its pending edits, or null when the window has passed. */
  fun resume(sessionId: SessionId, now: Duration): ResumedSession? {
    if (!canResume(sessionId, now)) return null
    val entry = held[sessionId] ?: return null
    return ResumedSession(sessionId, entry.edits.drain())
  }

  /** Ends every session past the window and reports the work each one lost. */
  fun expire(now: Duration): List<ExpiredSession> {
    val expired = held.filter { (_, entry) -> now - entry.since > window.duration }
    expired.keys.forEach(held::remove)
    return expired.map { (id, entry) -> ExpiredSession(id, entry.edits.drain()) }
  }
}
