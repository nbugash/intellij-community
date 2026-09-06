// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * The split-mode backend. It opens a project, holds it open, and serves state to a thin client.
 *
 * The command name is `splitBackend`, registered in `WellKnownCommand` with the mode
 * `HEADLESS_REMOTE_DEV_HOST`. The backend renders nothing, because the thin client builds the user
 * interface from state. See research decision D2.
 *
 * Usage: `<ide> splitBackend --project=<path>`
 */
internal class SplitBackendStarter : ModernApplicationStarter() {
  override val isHeadless: Boolean = true

  override suspend fun start(args: List<String>) {
    val paths = projectPaths(args)
    if (paths.isEmpty()) {
      abort("Give at least one project with $PROJECT_ARGUMENT<path>")
    }
    val projects = paths.mapNotNull { openProject(it) }
    if (projects.isEmpty()) {
      abort("No project opened. The backend has nothing to serve")
    }
    reportTrustState(projects)
    try {
      report("Backend ready. ${projects.size} project(s) open. Waiting for a client.")
      awaitCancellation()
    }
    finally {
      closeProjects(projects)
    }
  }

  /**
   * Opens a project without granting trust.
   *
   * FR-007 forbids running project-supplied code before the user grants trust. A headless starter
   * that force-trusts, as some do, would break that rule. The trust decision belongs to the user
   * through the client, so this method only reports the state it finds.
   */
  private suspend fun openProject(path: Path): Project? {
    val project = try {
      ProjectUtil.openOrImportAsync(file = path, options = OpenProjectTask {})
    }
    catch (failure: Exception) {
      report("Failed to open '$path': ${failure.message}. Check that the path exists on this host.")
      return null
    }
    if (project == null) {
      report("Could not open '$path'. Check that it is a project directory on this host.")
    }
    return project
  }

  private fun reportTrustState(projects: List<Project>) {
    projects.forEach { project ->
      val trusted = TrustedProjects.isProjectTrusted(project)
      report("Project '${project.name}' trusted=$trusted. An untrusted project runs no build script.")
    }
  }

  private suspend fun closeProjects(projects: List<Project>) {
    // Principle 4, Threading. Closing a project writes to the model, so it runs on the EDT inside a
    // write-intent read action. The loop lives in its own function to keep nesting at or under 3.
    withContext(Dispatchers.EDT) {
      writeIntentReadAction { disposeAll(projects) }
    }
    report("Projects closed.")
  }

  private fun disposeAll(projects: List<Project>) {
    val manager = ProjectManager.getInstance()
    projects.forEach(manager::closeAndDispose)
  }

  private fun projectPaths(args: List<String>): List<Path> =
    args.filter { it.startsWith(PROJECT_ARGUMENT) }
      .map { Path.of(it.removePrefix(PROJECT_ARGUMENT)) }
      .distinct()

  /**
   * Writes to the host's standard error.
   *
   * The text is [NonNls] on purpose. This runs on a host terminal, read by whoever started the
   * backend, and the platform does not localise `ApplicationStarter` console output either. Sending
   * it through a bundle would translate an operator's diagnostics into the end user's language.
   */
  private fun report(message: @NonNls String) {
    System.err.println("[splitBackend] $message")
  }

  private fun abort(message: @NonNls String): Nothing {
    report(message)
    exitProcess(EXIT_BAD_USAGE)
  }

  private companion object {
    const val PROJECT_ARGUMENT: String = "--project="
    const val EXIT_BAD_USAGE: Int = 1
  }
}
