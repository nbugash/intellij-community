// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.streams.asSequence
import kotlin.system.exitProcess

/**
 * Verifies what a build produced, per SC-013 and SC-014.
 *
 * Usage: `verify-artifacts <artifacts directory>`
 *
 * Exits non-zero when a component carries a licence nobody has approved, or when a produced file
 * holds something that looks like a credential. Both criteria ask for this on every build, and the
 * constitution's Verification Standards say compliance must not rest on review.
 */
object VerifyArtifacts {
  private val TEXT_EXTENSIONS = setOf("txt", "json", "html", "xml", "properties", "log", "yaml", "yml", "sh", "cmd")

  @JvmStatic
  fun main(args: Array<String>) {
    if (args.isEmpty()) {
      System.err.println("Usage: verify-artifacts <artifacts directory>")
      exitProcess(2)
    }
    val root = Path.of(args[0])
    val licenceReport = validateLicences(root)
    val findings = scanForSecrets(root)

    println(licenceReport?.summary ?: "No third-party licence report found under $root.")
    println(
      if (findings.isEmpty()) "Scanned for credentials. Nothing found."
      else "Possible credentials found:\n" + findings.joinToString("\n") { "  ${it.source}:${it.line} ${it.kind}" }
    )

    val failed = licenceReport?.isClean == false || findings.isNotEmpty()
    if (failed) exitProcess(1)
  }

  private fun validateLicences(root: Path): LicenseReport? {
    val report = textFiles(root).firstOrNull { it.name.endsWith("third-party-libraries.json") } ?: return null
    val entries = Json.parseToJsonElement(Files.readString(report)).jsonArray.map { element ->
      val fields = element.jsonObject
      LibraryLicense(
        name = fields["name"]?.jsonPrimitive?.content.orEmpty(),
        version = fields["version"]?.jsonPrimitive?.content,
        license = fields["license"]?.jsonPrimitive?.content.orEmpty(),
      )
    }
    return LicenseValidator.validate(entries)
  }

  private fun scanForSecrets(root: Path): List<SecretFinding> =
    textFiles(root).flatMap { file -> SecretScanner.scan(Files.readString(file), file.name) }.toList()

  /** Only text is scanned. A binary would produce noise, not findings. */
  private fun textFiles(root: Path): Sequence<Path> =
    if (!Files.isDirectory(root)) emptySequence()
    else Files.walk(root).asSequence().filter { Files.isRegularFile(it) && it.extension in TEXT_EXTENSIONS }
}
