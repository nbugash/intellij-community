// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.jetbrains.annotations.ApiStatus

/**
 * Masks a known credential in text bound for a log or a diagnostic report.
 *
 * The credential types in this module redact themselves, which covers the ordinary path. This covers
 * the other one: a raw value that reached a string some other way, such as through a message a peer
 * sent, or a stack trace that captured it.
 *
 * This is a second line, not the first. Relying on it alone would mean every new log statement is a
 * chance to leak, which is why [SessionToken] hides its own value.
 */
@ApiStatus.Internal
object LogRedaction {
  const val MASK: String = "<redacted>"

  /**
   * A value shorter than this is not masked. Masking a two character value would redact ordinary
   * words and leave a log that says nothing.
   */
  private const val SHORTEST_MASKABLE: Int = 8

  fun redact(text: String, secrets: List<String>): String =
    secrets.filter { it.isNotBlank() && it.length >= SHORTEST_MASKABLE }
      .fold(text) { masked, secret -> masked.replace(secret, MASK) }
}
