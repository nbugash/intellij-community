// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.SessionFailure
import org.jetbrains.annotations.ApiStatus

/**
 * What a client asks the backend to do, and whether doing it runs code that the project supplies.
 *
 * The distinction is the whole point of FR-007. Reading and navigating must work before trust,
 * otherwise a user cannot inspect a project in order to decide whether to trust it. Importing a
 * build script or running a configuration executes project-supplied code, so it must not.
 */
@ApiStatus.Internal
enum class BackendOperation(val runsProjectCode: Boolean) {
  READ_FILE(runsProjectCode = false),
  OPEN_EDITOR(runsProjectCode = false),
  SEARCH(runsProjectCode = false),
  IMPORT_BUILD_SCRIPT(runsProjectCode = true),
  RUN_CONFIGURATION(runsProjectCode = true),
  EXECUTE_TEST(runsProjectCode = true),
  LOAD_PROJECT_PLUGIN(runsProjectCode = true),
}

/**
 * The single place that decides whether an operation may proceed without project trust.
 *
 * FR-007 and SC-015 depend on this rule holding everywhere, so the rule lives in one function rather
 * than at each call site.
 */
@ApiStatus.Internal
object BackendTrustGate {
  /** Returns the failure to report, or null when the operation may proceed. */
  fun refusalFor(operation: BackendOperation, trusted: Boolean): SessionFailure? =
    if (operation.runsProjectCode && !trusted) SessionFailure.TRUST_REQUIRED else null
}
