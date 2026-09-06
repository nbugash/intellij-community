// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.PendingEdit
import org.jetbrains.annotations.ApiStatus

/**
 * The documents a resumed session can write into.
 *
 * This interface is the seam between the replay policy and the platform's shared document model.
 * The policy is what carries the risk of losing an edit, and a test must be able to drive it; the
 * production implementation needs a live kernel transaction, which no unit test can open.
 */
@ApiStatus.Internal
interface SharedDocuments {
  /** Applies [text] to the document at [path]. Returns false when the host cannot open it. */
  fun apply(path: String, text: String): Boolean
}

/** What a replay managed to do. */
@ApiStatus.Internal
data class ReplayReport(val applied: Int, val rejected: List<PendingEdit>) {
  val isComplete: Boolean get() = rejected.isEmpty()

  val summary: String
    get() = if (isComplete) "Replayed $applied edit(s)."
    else "Replayed $applied edit(s). Could not apply ${rejected.size}: " +
         rejected.joinToString(", ") { it.path } + "."
}

/**
 * Applies the edits a client made while it was disconnected.
 *
 * Two rules matter, and both come from FR-015's ban on silent loss.
 *
 * The edits are applied in the order they were made. Two edits to one file in the wrong order
 * produce different content.
 *
 * A rejected edit does not stop the replay. Stopping at the first rejection would lose every edit
 * behind it. Each one is attempted and the failures are collected, so a caller can put them where
 * the user recovers them.
 */
@ApiStatus.Internal
object DocumentReplay {
  fun replay(edits: List<PendingEdit>, documents: SharedDocuments): ReplayReport {
    val rejected = mutableListOf<PendingEdit>()
    var applied = 0
    edits.forEach { edit ->
      if (documents.apply(edit.path, edit.text)) applied++ else rejected += edit
    }
    return ReplayReport(applied, rejected)
  }
}
