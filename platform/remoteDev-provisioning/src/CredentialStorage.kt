// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.generateServiceName
import org.jetbrains.annotations.ApiStatus

/**
 * Puts every host credential in the platform credential store, and hands back a reference.
 *
 * FR-008 says a credential never reaches a file under version control and never reaches a log. The
 * way to keep that promise is to give the rest of the feature no way to hold a secret: [HostRecord]
 * takes a [CredentialRef], and only this class can turn one back into a value.
 *
 * The platform store is the right home for it because it already chooses the operating system
 * keychain where there is one, and it already asks the user about the fallback where there is not.
 *
 * This takes the platform [CredentialStore] interface, and does not define one of its own. A
 * wrapper interface here would add a name and no behaviour, and a test drives the platform
 * interface just as easily.
 *
 * Every method reads or writes the keychain, so every method wants a background thread. The
 * platform annotates [CredentialStore] with that requirement, and it carries through.
 */
@ApiStatus.Internal
class CredentialStorage(private val store: CredentialStore) {
  /** Stores [secret] for [hostId], replacing any secret already held for that host. */
  fun store(hostId: HostId, secret: String): CredentialRef {
    val ref = CredentialRef(hostId.value)
    store.setPassword(attributesFor(ref), secret)
    return ref
  }

  fun retrieve(ref: CredentialRef): String? = store.getPassword(attributesFor(ref))

  /** Removes the secret. FR-018 asks for revocation that takes effect at once, so this deletes. */
  fun revoke(ref: CredentialRef) {
    store.set(attributesFor(ref), null)
  }

  private fun attributesFor(ref: CredentialRef): CredentialAttributes =
    CredentialAttributes(generateServiceName(SUBSYSTEM, ref.value))

  private companion object {
    /** Part of the key the credential is stored under. Changing it hides every stored secret. */
    const val SUBSYSTEM = "Remote Development"
  }
}
