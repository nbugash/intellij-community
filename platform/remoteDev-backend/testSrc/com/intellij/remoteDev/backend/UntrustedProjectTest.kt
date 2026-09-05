// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.openapi.util.SystemInfo
import com.intellij.remoteDev.protocol.SessionFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * SC-015. A project the user has not trusted does not get to run its code on the host.
 *
 * The build script here is real, and running it leaves a file behind. That matters more than it
 * looks: a test that asserts "the script did not run" passes just as well when nothing ever tried
 * to run it, and such a test would report success for a host with no protection at all.
 *
 * [a trusted project runs the build script] is the control. It runs the same script through the
 * same dispatcher, and it fails if the script is not really executable. The two tests are only
 * worth having together.
 */
class UntrustedProjectTest {
  @TempDir
  lateinit var projectDir: Path

  private lateinit var sentinel: Path
  private lateinit var buildScript: Path

  @BeforeEach
  fun writeProject() {
    // The host runs Linux or macOS. See ThinClientInstallersBuildTarget for why those two.
    assumeFalse(SystemInfo.isWindows, "The test runs a shell script")

    sentinel = projectDir.resolve("the-script-ran")
    buildScript = projectDir.resolve("build.sh")
    buildScript.writeText("#!/bin/sh\ntouch '${sentinel}'\n")
    assertTrue(buildScript.toFile().setExecutable(true))
  }

  private fun runBuildScript(): Int =
    ProcessBuilder("/bin/sh", buildScript.toString()).start().waitFor()

  private fun dispatcher(trusted: Boolean) = BackendOperationDispatcher { trusted }

  @Test
  fun `an untrusted project does not run the build script`() {
    var actionReached = false

    val result = dispatcher(trusted = false).dispatch(BackendOperation.IMPORT_BUILD_SCRIPT, projectDir.toString()) {
      actionReached = true
      runBuildScript()
    }

    assertEquals(DispatchResult.Refused(SessionFailure.TRUST_REQUIRED), result)
    assertFalse(actionReached, "The dispatcher called the action for an untrusted project")
    assertFalse(sentinel.exists(), "The build script ran for an untrusted project")
  }

  @Test
  fun `a trusted project runs the build script`() {
    // The control. Without it, the test above passes for a script that could never run.
    val result = dispatcher(trusted = true).dispatch(BackendOperation.IMPORT_BUILD_SCRIPT, projectDir.toString()) {
      runBuildScript()
    }

    assertEquals(DispatchResult.Ran(0), result)
    assertTrue(sentinel.exists(), "The build script did not run for a trusted project")
  }

  @Test
  fun `reading a file works before the user grants trust`() {
    // FR-007. A user decides whether to trust a project by looking at it, so looking must work.
    var actionReached = false

    val result = dispatcher(trusted = false).dispatch(BackendOperation.READ_FILE, projectDir.toString()) {
      actionReached = true
      "the file content"
    }

    assertEquals(DispatchResult.Ran("the file content"), result)
    assertTrue(actionReached)
  }

  @Test
  fun `every operation that runs project code is refused before trust`() {
    val refused = BackendOperation.entries.filter { operation ->
      dispatcher(trusted = false).dispatch(operation, projectDir.toString()) { } is DispatchResult.Refused
    }

    assertEquals(BackendOperation.entries.filter { it.runsProjectCode }, refused)
    assertFalse(sentinel.exists())
  }
}
