// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where to reach a host, and how to authenticate to it. */
@ApiStatus.Internal
data class SshTarget(
  val host: String,
  val user: String? = null,
  val port: Int = 22,
  val identityFile: Path? = null,
) {
  /** What `ssh` wants as its destination. */
  val destination: String get() = if (user == null) host else "$user@$host"
}

/**
 * A [HostBootstrap] that reaches the host with the system `ssh` and `scp`.
 *
 * ### Why the system binaries
 *
 * Bootstrap means getting an agent onto a host that has none. Every richer transport in the platform
 * assumes an agent is already there: `SshEelDescriptor` in `eel-tcp` speaks to an IJent over TCP,
 * which is exactly what does not exist yet at this point. `ssh` is what a bare host answers.
 *
 * ### Host keys
 *
 * `StrictHostKeyChecking` defaults to `accept-new`, not `no`. The difference matters. `accept-new`
 * trusts a host it has never seen, which a first bootstrap must do, and still refuses a host whose
 * key has *changed*, which is the case that indicates an interception. Turning the check off
 * entirely would discard the second protection to gain nothing.
 *
 * `BatchMode=yes` is not a convenience either. Without it, a host that wants a password blocks on a
 * prompt that nothing will ever answer, and the bootstrap hangs instead of failing.
 */
@ApiStatus.Internal
class SshHostBootstrap(
  private val target: SshTarget,
  private val hostKeyPolicy: String = "accept-new",
  private val readyTimeout: Duration = 10.seconds,
) : HostBootstrap, AutoCloseable {
  private val tunnels = mutableListOf<Process>()

  override suspend fun upload(local: Path, remotePath: String) {
    val command = buildList {
      add("scp")
      addAll(commonOptions())
      add("-P")
      add(target.port.toString())
      add(local.toString())
      add("${target.destination}:$remotePath")
    }
    val exit = run(command)
    if (exit != 0) throw IOException("Uploading $local to $remotePath failed with exit code $exit")
  }

  override suspend fun execute(command: List<String>): Int = run(sshCommand(quote(command)))

  /**
   * Opens a local port that reaches [remotePort] on the host.
   *
   * The local port is chosen by binding port 0 and reading what the operating system gave, then
   * releasing it for `ssh` to take. Two processes can in principle race for it in between. The
   * alternative is asking `ssh -L 0:` to choose, which does not report its choice back in a form
   * worth parsing, so this takes the race and fails loudly rather than guessing a port.
   */
  override suspend fun forwardPort(remotePort: Int): Int {
    val localPort = freeLocalPort()
    val command = sshCommand(forwarding = "$localPort:127.0.0.1:$remotePort")
    val process = withContext(Dispatchers.IO) { ProcessBuilder(command).inheritIO().start() }
    synchronized(tunnels) { tunnels += process }
    awaitListening(localPort, process)
    return localPort
  }

  /** Closes every tunnel this instance opened. A leaked `ssh -N` outlives the session otherwise. */
  override fun close() {
    val open = synchronized(tunnels) { tunnels.toList().also { tunnels.clear() } }
    open.forEach { it.destroy() }
  }

  private fun sshCommand(remoteCommand: String? = null, forwarding: String? = null): List<String> =
    buildList {
      add("ssh")
      addAll(commonOptions())
      add("-p")
      add(target.port.toString())
      if (forwarding != null) {
        add("-N")
        add("-L")
        add(forwarding)
      }
      add(target.destination)
      if (remoteCommand != null) add(remoteCommand)
    }

  private fun commonOptions(): List<String> = buildList {
    add("-o"); add("BatchMode=yes")
    add("-o"); add("StrictHostKeyChecking=$hostKeyPolicy")
    target.identityFile?.let { add("-i"); add(it.toString()) }
  }

  private suspend fun run(command: List<String>): Int = withContext(Dispatchers.IO) {
    ProcessBuilder(command).inheritIO().start().waitFor()
  }

  /** Waits until the tunnel accepts a connection, or the process dies, or the timeout passes. */
  private suspend fun awaitListening(port: Int, process: Process) = withContext(Dispatchers.IO) {
    val deadline = System.nanoTime() + readyTimeout.inWholeNanoseconds
    while (System.nanoTime() < deadline) {
      if (!process.isAlive) throw IOException("The tunnel exited with code ${process.exitValue()}")
      if (canConnect(port)) return@withContext
      Thread.sleep(POLL_MILLIS)
    }
    process.destroy()
    throw IOException("The tunnel on port $port was not ready within $readyTimeout")
  }

  private fun canConnect(port: Int): Boolean =
    runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MILLIS) } }.isSuccess

  private fun freeLocalPort(): Int = ServerSocket(0).use { it.localPort }

  companion object {
    private const val POLL_MILLIS = 50L
    private const val CONNECT_TIMEOUT_MILLIS = 250

    /**
     * Quotes a command for the remote shell.
     *
     * `ssh` does not pass arguments through. It joins them with spaces and hands the result to the
     * host's shell, which parses it again. Without quoting, an argument containing `;` or a
     * backtick executes on the host, so `execute(listOf("echo", "a; rm -rf ~"))` would delete a home
     * directory. Single quotes suppress every metacharacter, and a single quote inside is closed,
     * escaped and reopened, which is the only sequence that survives.
     */
    fun quote(command: List<String>): String =
      command.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
  }
}
