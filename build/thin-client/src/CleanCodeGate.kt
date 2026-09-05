// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

/** One place a file breaks constitution Principle 2. */
data class CleanCodeOutlier(val file: String, val line: Int, val rule: String, val detail: String) {
  override fun toString(): String = "$file:$line $rule: $detail"
}

/**
 * The lint gate for constitution Principle 2, Clean Code, task T123.
 *
 * ### What this enforces, and what it deliberately does not
 *
 * Principle 2 lists eight rules. This gate enforces the three that can be decided from source text
 * without resolving types: function length, nesting depth, and commented-out code. It reports every
 * outlier by name, which Verification Standards requires.
 *
 * The other five are not enforced here, and the reason is the same for each: a check that is
 * usually right is worse than no check, because a gate that reports nothing is read as a gate that
 * found nothing.
 *
 * *Cyclomatic complexity* needs a parser. Counting branch keywords in text cannot tell an exhaustive
 * `when` over a sealed type from an ordinary one, and Principle 2 exempts the first. Detekt's
 * `CyclomaticComplexMethod` has no such option either, so adopting it would enforce a different rule
 * than the one written down.
 *
 * *Magic numbers and magic strings* need to know that `1` in `size - 1` is an idiom and `86400` is a
 * day. Every text-level version of this rule is mostly false positives.
 *
 * *Dead code* needs whole-program reachability. An unused `internal` function is dead; an unused
 * `public` one may be API. The `api-dump.txt` files are the closer tool for that question.
 *
 * *YAGNI* and *naming* are review questions. No linter decides them.
 *
 * So this gate is a floor, not a certificate. Principle 2 is still enforced by review; this catches
 * the three failures review misses most often because they need counting rather than judgement.
 */
object CleanCodeGate {
  const val MAX_FUNCTION_LINES: Int = 30
  const val MAX_NESTING_DEPTH: Int = 3

  fun check(fileName: String, source: String): List<CleanCodeOutlier> {
    val lines = stripCommentsAndStrings(source)
    return functionOutliers(fileName, lines) + commentedOutCode(fileName, source)
  }

  /**
   * Blanks out comment bodies and string contents, keeping line numbers and structural braces.
   *
   * Every rule below counts braces, and a brace inside `"${'$'}{x}"` or inside a comment is not
   * structure. Counting the raw text was the first version and it reported nonsense on any file
   * containing a string with a brace in it.
   */
  internal fun stripCommentsAndStrings(source: String): List<String> {
    var inBlockComment = false
    return source.lines().map { line ->
      val (cleaned, stillInComment) = stripLine(line, inBlockComment)
      inBlockComment = stillInComment
      cleaned
    }
  }

  private fun stripLine(line: String, startsInComment: Boolean): Pair<String, Boolean> {
    val out = StringBuilder()
    var inComment = startsInComment
    var inString = false
    var index = 0
    while (index < line.length) {
      val rest = line.length - index
      when {
        inComment && rest >= 2 && line.startsWith("*/", index) -> { inComment = false; index += 2 }
        inComment -> index++
        inString -> { if (line[index] == '"' && line.getOrNull(index - 1) != '\\') inString = false; index++ }
        rest >= 2 && line.startsWith("/*", index) -> { inComment = true; index += 2 }
        rest >= 2 && line.startsWith("//", index) -> index = line.length
        line[index] == '"' -> { inString = true; index++ }
        line[index] == '\'' -> index = skipCharLiteral(line, index)
        else -> { out.append(line[index]); index++ }
      }
    }
    return out.toString() to inComment
  }

  /**
   * Skips a character literal.
   *
   * This gate found its own need for it: `'"'` put the stripper into string mode and `'{'` counted
   * as structure, so the gate reported its own [stripLine] as 76 lines. A brace or a quote inside a
   * character literal is not structure, exactly as inside a string.
   */
  private fun skipCharLiteral(line: String, start: Int): Int {
    var index = start + 1
    while (index < line.length) {
      if (line[index] == '\\') { index += 2; continue }
      if (line[index] == '\'') return index + 1
      index++
    }
    return index
  }

  private fun functionOutliers(fileName: String, lines: List<String>): List<CleanCodeOutlier> {
    val scan = FunctionScan(fileName)
    lines.forEachIndexed(scan::consume)
    return scan.outliers
  }

  /**
   * Walks the file once, tracking brace depth and the function currently open.
   *
   * This is a class rather than a loop with local variables because the loop version nested four
   * deep and this gate reported it. Guard clauses over nested `if` is Principle 2's own advice.
   */
  private class FunctionScan(private val fileName: String) {
    val outliers: MutableList<CleanCodeOutlier> = mutableListOf()
    private var open: FunctionSpan? = null
    private var depth = 0

    fun consume(index: Int, line: String) {
      startIfFunction(index, line)
      depth += line.count { it == '{' } - line.count { it == '}' }
      val span = open ?: return
      span.deepest = maxOf(span.deepest, depth - span.baseDepth)
      if (depth > span.baseDepth) return
      outliers += span.close(fileName, index + 1)
      open = null
    }

    private fun startIfFunction(index: Int, line: String) {
      if (open != null) return
      if (!FUNCTION_START.containsMatchIn(line) || !line.contains("{")) return
      open = FunctionSpan(name(line), index + 1, depth)
    }
  }

  private class FunctionSpan(val name: String, val startLine: Int, val baseDepth: Int) {
    var deepest: Int = 0

    fun close(fileName: String, endLine: Int): List<CleanCodeOutlier> = buildList {
      val length = endLine - startLine + 1
      if (length > MAX_FUNCTION_LINES) {
        add(CleanCodeOutlier(fileName, startLine, "function-length", "$name is $length lines, limit $MAX_FUNCTION_LINES"))
      }
      if (deepest > MAX_NESTING_DEPTH) {
        add(CleanCodeOutlier(fileName, startLine, "nesting-depth", "$name nests $deepest deep, limit $MAX_NESTING_DEPTH"))
      }
    }
  }

  /**
   * Flags a comment that is code rather than prose.
   *
   * The test is deliberately narrow: a `//` comment whose text ends in `;`, `{`, or `}`, or reads as
   * an assignment or a call statement. Prose does not end in a brace. A wider test flags every
   * comment containing a code fragment, and this codebase's comments name types on purpose.
   */
  private fun commentedOutCode(fileName: String, source: String): List<CleanCodeOutlier> =
    source.lines().mapIndexedNotNull { index, line ->
      val text = line.trim().removePrefix("//").trim()
      if (!line.trim().startsWith("//") || text.isEmpty()) return@mapIndexedNotNull null
      if (!COMMENTED_CODE.containsMatchIn(text)) return@mapIndexedNotNull null
      CleanCodeOutlier(fileName, index + 1, "commented-out-code", text.take(60))
    }

  private fun name(line: String): String =
    FUNCTION_NAME.find(line)?.groupValues?.get(1) ?: line.trim().take(40)

  private val FUNCTION_START = Regex("""(^|\s)(fun|constructor)\s""")
  private val FUNCTION_NAME = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)""")
  private val COMMENTED_CODE = Regex("""[;{}]$|^\w[\w.]*\s*=[^=]|^(val|var|return|if|for|while)\s""")
}

/**
 * Runs the gate over the directories named on the command line and prints every outlier.
 *
 * Exits non-zero when there is at least one, so it can gate a build. Verification Standards asks for
 * outliers by name, so it prints them rather than only counting.
 */
object CleanCodeGateMain {
  @JvmStatic
  fun main(args: Array<String>) {
    val roots = args.toList().ifEmpty { error("Give at least one directory to check") }
    val outliers = roots.flatMap { root -> check(java.nio.file.Path.of(root)) }
    outliers.forEach(::println)
    println("${outliers.size} outlier(s) in ${roots.size} root(s)")
    if (outliers.isNotEmpty()) kotlin.system.exitProcess(1)
  }

  private fun check(root: java.nio.file.Path): List<CleanCodeOutlier> =
    java.nio.file.Files.walk(root).use { paths ->
      paths.filter { it.toString().endsWith(".kt") }
        .map { CleanCodeGate.check(root.relativize(it).toString(), java.nio.file.Files.readString(it)) }
        .toList().flatten()
    }
}
