// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * A version of the session protocol.
 *
 * The contract in `specs/001-ultimate-feature-parity/contracts/session-protocol.md` is the source of
 * truth for the wire behaviour. This type carries the version and its order. It does not carry the
 * behaviour of any single version.
 */
@ApiStatus.Internal
@Serializable
@JvmInline
value class ProtocolVersion(val number: Int) : Comparable<ProtocolVersion> {
  init {
    require(number >= EARLIEST_NUMBER) { "A protocol version starts at $EARLIEST_NUMBER, got $number" }
  }

  override fun compareTo(other: ProtocolVersion): Int = number.compareTo(other.number)

  override fun toString(): String = number.toString()

  companion object {
    const val EARLIEST_NUMBER: Int = 1
  }
}

/**
 * The versions this build speaks, and the rule that decides how many a host advertises.
 */
@ApiStatus.Internal
object ProtocolVersions {
  /** The version this build speaks. */
  val CURRENT: ProtocolVersion = ProtocolVersion(1)

  /** FR-057. A host supports at least the two most recent versions. */
  const val ADVERTISED_COUNT: Int = 2

  fun supported(): List<ProtocolVersion> = supportedBy(CURRENT)

  /** The versions a host on [current] advertises, most recent first. */
  fun supportedBy(current: ProtocolVersion): List<ProtocolVersion> {
    val earliest = maxOf(ProtocolVersion.EARLIEST_NUMBER, current.number - ADVERTISED_COUNT + 1)
    return (current.number downTo earliest).map(::ProtocolVersion)
  }
}

/**
 * The version rule from contract section 3.3.
 *
 * The host takes the first version the client offers that the host also supports. The client orders
 * its offer, most recent first, so the client decides the preference.
 */
@ApiStatus.Internal
object VersionNegotiator {
  fun select(offered: List<ProtocolVersion>, supported: List<ProtocolVersion>): ProtocolVersion? =
    offered.firstOrNull(supported::contains)
}
