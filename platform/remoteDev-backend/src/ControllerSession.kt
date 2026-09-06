// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.client.ClientAppSessionImpl
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.client.ClientType
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * The application-level session of one connected thin client.
 *
 * `ClientType.CONTROLLER` is the platform's own term for a remote owner that drives the IDE from
 * outside, which is exactly a thin client. Reusing it means every per-client service in the platform
 * resolves correctly for this session with no further work.
 */
@ApiStatus.Internal
internal class ControllerAppSession(
  clientId: ClientId,
  application: ApplicationImpl,
) : ClientAppSessionImpl(clientId, ClientType.CONTROLLER, application) {
  override val name: String = "Remote client ${clientId.value}"
}

/**
 * Registers a controller session with the platform.
 *
 * T122 established that this needs no change to `ClientSessionManagerImpl`.
 * `ClientSessionsManager.registerSession` is public, `ClientAppSession` is an interface, and the
 * manager is a service declared `open="true"`. Community registers only local sessions, which is why
 * the plan first assumed an upstream edit was required.
 */
@ApiStatus.Internal
internal object ControllerSessionRegistrar {
  fun register(application: ApplicationImpl, disposable: Disposable, clientId: ClientId): ClientAppSession {
    val session = ControllerAppSession(clientId, application)
    application.service<ClientSessionsManager<ClientAppSession>>().registerSession(disposable, session)
    return session
  }
}
