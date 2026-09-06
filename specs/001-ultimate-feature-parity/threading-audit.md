# Threading audit

Constitution Principle 4, Threading, applied to every RPC handler and background path in
`platform/remoteDev-backend` and `platform/remoteDev-provisioning`. Task T120.

## Method

The audit looked for three things: a PSI, VFS, or project-model read outside a read action; a write
to the model off the EDT or outside a write action; and a long read with no way to cancel it.

The starting point was every call into a platform API from those two modules, rather than every
function, because a module that touches no platform API has no threading contract to break.

## What the two modules actually touch

`platform/remoteDev-provisioning` touches no PSI, no VFS, and no project model. Its state is its own:
`HostRegistry`, `PortForwarder`, and `SessionTokenRegistry` hold `ConcurrentHashMap`s, and
`AdminHostPolicy` reads the operating system through a lambda. Nothing there needs a read action,
and adding one would be noise. The one threading requirement it does carry is `CredentialStorage`,
which reads and writes the platform credential store; `CredentialStore` is annotated
`@RequiresBackgroundThread` and `@RequiresReadLockAbsence` upstream, and that requirement carries
through to every caller.

`platform/remoteDev-backend` touches the platform in four places, listed below.

## Findings

### 1. `BackendSessionApi.isOpenHere` read open projects unguarded. Fixed.

```kotlin
ProjectManager.getInstance().openProjects.any { it.basePath == path }
```

`getOpenProjects` documents its own contract: *"`Project#isDisposed()` must be checked for each
project before use (if the whole operation is not under read action)."* This did neither, and it is
reachable from the `handshake` RPC handler through `HostSessionPolicy.projectAvailability`. The
returned array is a snapshot, so a project disposed just after it is taken gives undefined
behaviour on the `basePath` read.

Fixed by checking `isDisposed` per project, which is the alternative the platform itself offers.

A read action was the other option and was rejected. This runs on the RPC thread answering a
handshake, and taking the read lock there would block that answer behind any write the host happens
to be doing. The check costs one predicate and blocks nothing.

### 2. `SplitBackendStarter.closeProjects` was already correct.

Closing a project writes to the model, and the code runs it on `Dispatchers.EDT` inside
`writeIntentReadAction`. It carries a comment saying why. No change.

### 3. `SplitBackendStarter.openProject` delegates its threading.

`ProjectUtil.openOrImportAsync` is a suspend function that manages its own dispatchers. Wrapping it
would be wrong, not merely redundant. No change.

### 4. `SplitBackendStarter.reportTrustState` reads trust state off any thread.

`TrustedProjects.isProjectTrusted` carries no read-lock annotation upstream, and it resolves to a
stored setting rather than a model traversal. Left as is.

This one is recorded rather than closed. The absence of an annotation is weaker evidence than the
presence of one, and if the platform later moves that lookup behind the project model, this call
becomes a finding. It is a single call in a starter that runs once at boot, so the cost of being
wrong is small and visible.

## Cancellation

No path in either module performs a long read. The longest operations are network and process work
in `HostBootstrap` and `PortForwarder`, which run in coroutines and cancel with their scope. There
is no loop over PSI or VFS anywhere in the fork, so there is nothing that needs
`ProgressManager.checkCanceled`.

This will change the moment a feature service arrives. A search or an inspection over a real project
is exactly the long read Principle 4 has in mind, and the audit should be re-run when the first one
lands.

## Standing gap

The audit is a reading of the code, and reading does not scale. Nothing today stops a future RPC
handler from reading the project model on the RPC thread, because nothing checks. The lint gate in
T123 is where such a check would live.
