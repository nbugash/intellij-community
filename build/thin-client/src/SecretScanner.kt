// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

/** Where a possible secret was seen. */
data class SecretFinding(val source: String, val line: Int, val kind: String)

/**
 * Looks for a credential in text that a build produced.
 *
 * SC-013 asks for this over every produced artifact, and FR-008 forbids a credential in a log or a
 * report. The patterns are deliberately few and high signal. A scanner that cries wolf is switched
 * off, and a switched-off scanner finds nothing at all, so a pattern earns its place only when a
 * match is almost certainly a real credential.
 *
 * The redaction placeholders this fork emits are not findings. `SessionToken` and `CredentialRef`
 * print a fixed marker instead of their value, and flagging that marker would punish the very
 * behaviour FR-008 asks for.
 */
object SecretScanner {
  private val PATTERNS: List<Pair<String, Regex>> = listOf(
    "private key" to Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    "aws secret" to Regex("""aws_secret_access_key\s*[=:]\s*\S+""", RegexOption.IGNORE_CASE),
    "password assignment" to Regex("""\b(password|passwd|pwd)\s*[=:]\s*(?!["']?\s*$)\S+""", RegexOption.IGNORE_CASE),
    "bearer token" to Regex("""\bBearer\s+[A-Za-z0-9\-._~+/]{20,}"""),
  )

  /** Text this fork prints deliberately in place of a credential. A match here is not a finding. */
  private val REDACTIONS: List<String> = listOf("SessionToken(redacted)", "CredentialRef(hidden)")

  fun scan(text: String, source: String): List<SecretFinding> =
    text.lineSequence().withIndex().flatMap { (index, line) ->
      if (REDACTIONS.any(line::contains)) emptySequence()
      else PATTERNS.asSequence()
        .filter { (_, pattern) -> pattern.containsMatchIn(line) }
        .map { (kind, _) -> SecretFinding(source, index + 1, kind) }
    }.toList()
}
