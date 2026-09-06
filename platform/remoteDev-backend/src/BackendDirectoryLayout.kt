// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The identity of one backend on a host.
 *
 * The value becomes a directory name, so it must not be able to leave the root that
 * [BackendDirectoryLayout] resolves against. The constructor refuses anything that could.
 */
@ApiStatus.Internal
@JvmInline
value class BackendId(val value: String) {
  init {
    require(value.isNotBlank()) { "A backend id must not be blank" }
    require(value.all(::isAllowed)) { "A backend id accepts a letter, a digit, '-' and '_' only, got '$value'" }
  }

  private companion object {
    fun isAllowed(character: Char): Boolean = character.isLetterOrDigit() || character == '-' || character == '_'
  }
}

/**
 * Where one backend keeps its own state.
 *
 * FR-019 requires several backends to run on one host at the same time. They interfere if they share
 * a configuration directory or a system directory, because the platform writes caches, indexes and
 * settings there. Each backend therefore gets its own subtree under the root.
 */
@ApiStatus.Internal
object BackendDirectoryLayout {
  private const val SYSTEM_DIR_NAME: String = "system"
  private const val CONFIG_DIR_NAME: String = "config"

  fun systemDir(root: Path, id: BackendId): Path = root.resolve(id.value).resolve(SYSTEM_DIR_NAME)

  fun configDir(root: Path, id: BackendId): Path = root.resolve(id.value).resolve(CONFIG_DIR_NAME)
}
