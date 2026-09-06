// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.openapi.components.Service
import com.intellij.remoteDev.protocol.SessionId
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * The sessions this backend currently serves.
 *
 * FR-019 allows several backends on one host, and contract section 4 allows one backend to hold a
 * session across a disconnection. The registry is the record of which sessions exist, so a second
 * client asking for a project that is already held can be refused with `PROJECT_LOCKED`.
 *
 * The map is concurrent because requests arrive on threads the IDE does not own.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
internal class BackendSessionRegistry {
  private val projectBySession = ConcurrentHashMap<SessionId, String>()

  fun remember(sessionId: SessionId, projectPath: String) {
    projectBySession[sessionId] = projectPath
  }

  fun forget(sessionId: SessionId) {
    projectBySession.remove(sessionId)
  }

  fun holdsProject(projectPath: String): Boolean = projectBySession.containsValue(projectPath)

  fun size(): Int = projectBySession.size
}
