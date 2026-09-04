# Fork Platform Changes

This file is the ledger that constitution v1.0.0 Principle 1, Extension Point First, requires. It
also serves FR-054, which states that the fork must stay rebasable on upstream Community Edition.

Every change to a file that upstream also maintains gets one row here. The row states why the change
could not be made in fork-owned code. A change with no row is a review failure.

**Current count of changed upstream source files: 4. Planned: 0.**

Keeping this number small is the main long-term cost control for the fork. Each entry must be
re-applied, re-tested, and re-argued at every upstream release.

### Registry and generated files, excluded from the count

Two tracked files change whenever a module is added. They are not source edits and they are not
optional, so they are recorded here rather than counted above.

- `.idea/modules.xml` ... the module registry. Adding a module requires an entry. The current diff
  is 9 insertions and 0 deletions, one for each new module descriptor. Never hand-edit it. Use
  `build/jps-module.mjs register`.
- `build/bazel-generated-file-list.txt` ... generator output. `jpsModelToBazelCommunityOnly.cmd`
  rewrites it. Both files already carried uncommitted changes before this work began.

## Applied changes

### 1. `platform/main/intellij.platform.backend.main/intellij.platform.backend.main.iml`
### 2. `platform/main/intellij.platform.frontend.main/intellij.platform.frontend.main.iml`

- **Tasks**: T019 and T020
- **Change**: one `orderEntry` added to each, attaching `intellij.platform.remoteDev.backend` and
  `intellij.platform.remoteDev.frontend` to the product root modules.
- **Why this cannot be avoided**: these two modules are the aggregators that define what a backend
  product and a frontend product contain. A module joins a product by appearing here. There is no
  extension point, because the list is consumed by the build, not by the running application.
- **Rebase note**: each change is a single line in a sorted list of `orderEntry` rows. A conflict
  would be trivial to resolve. Both shells already list every other `*.backend` and `*.frontend`
  module, so this follows the established pattern exactly.
- **Correction to the plan**: `plan.md` described these as "existing empty shells". They are not
  empty. The backend shell already carries 25 runtime dependencies and the frontend shell 16.

### 3. `platform/core-api/src/com/intellij/idea/WellKnownCommand.java` (APPLIED)

- **Task**: T030
- **Change**: `put("splitBackend", HEADLESS_REMOTE_DEV_HOST);` plus a two-line comment. 3 lines.
- **Why an extension point cannot do this**: `AppMode.setFlags(args)` is the first call in
  `mainImpl`, before `PathManager.loadProperties()` runs. It reads `args[0]` against a hardcoded
  command list to derive `isHeadless`, `isCommandLine`, and `isRemoteDevHost`. The plugin container
  does not exist at that point, so no extension point can be resolved.
- **Correction to the plan**: the plan assumed an already-reserved name could be reused, so that no
  new name was added. That is wrong. Every reserved remote name maps to `REMOTE_DEV_HOST`, which is
  `isHeadless=false`. That is the Lux design, where real Swing runs on the host with faked peers.
  Research decision D2 chose split mode instead, so this backend is headless and needs
  `HEADLESS_REMOTE_DEV_HOST`.
- **Rebase note**: `HEADLESS_REMOTE_DEV_HOST` already exists in this file and `provisionTbeBackend`
  already uses it, so the change introduces no constant. It adds one entry to a `put` list. A
  conflict would be trivial to resolve.

## Changes that were considered and avoided

### `platform/platform-impl/src/com/intellij/openapi/client/ClientSessionManagerImpl.kt`

- **Task**: T032, now removed. Investigated under T122.
- **Original plan**: modify the file to register a `ClientType.CONTROLLER` session, because
  `ClientSessionManagerImpl` registers only local sessions in Community.
- **Finding**: the modification is unnecessary. Three facts decide it.
  1. `ClientSessionsManager.registerSession(disposable, session)` is public. See
     `platform/core-api/src/com/intellij/openapi/client/ClientSessionsManager.kt:148`.
  2. `ClientAppSession` and `ClientProjectSession` are interfaces, not classes, so a fork-owned
     implementation is possible. See `platform/core-api/src/com/intellij/openapi/client/ClientSession.kt:57`
     and `:76`.
  3. `ClientAppSessionsManager` and `ClientProjectSessionsManager` are registered as services in
     `platform/platform-impl/resources/intellij.platform.ide.impl.xml:1845` and `:1847`. The platform
     supports `overrides="true"` on a service declaration, and the platform itself uses that
     mechanism. A subclass is therefore reachable without editing the file.
- **Corroborating evidence**: a comment inside `registerSession` names the upstream test
  `RdSeamlessReconnectTest.testReconnect_WireStorageBufferOverflow_Controller`. This confirms that a
  controller session is registered through this public method upstream, not through a private path.
- **Result**: the count of changed upstream files drops from two to one.

### 4. `build/dev-build.json` (APPLIED)

- **Task**: T046
- **Change**: one `thinClient` entry, 7 lines, naming the product's modules and its properties class.
- **Why this cannot be avoided**: this file is `PRODUCT_REGISTRY_PATH`. The Product DSL generator
  reads it to discover a product and to emit that product's descriptors. Without an entry, no
  descriptor is generated and the installer build cannot run.
- **Correction**: this task was first deferred as optional, on the grounds that PyCharm Community
  and MPS ship installers without appearing here. That reasoning was wrong. Those products do not
  need the generator, because their descriptors are already checked in.
- **Rebase note**: one object added to a JSON map. A conflict would be trivial.

## Hand-written files that upstream generates

Four files in this fork are written by hand where upstream generates them from Kotlin. Each carries
a comment saying so.

- `platform/remoteDev-frontend/resources/META-INF/ThinClientPlugin.xml`
- `platform/remoteDev-frontend/resources/META-INF/intellij.platform.remoteDev.frontend/product-modules.xml`
- `platform/remoteDev-frontend/resources/intellij.platform.remoteDev.frontend.xml`
- `platform/remoteDev-protocol/resources/intellij.platform.remoteDev.protocol.xml`

The generator could not be run. Its README names `//platform/buildScripts:plugin-model-tool`, and no
such package exists: the directory is `platform/build-scripts`, and no `BUILD.bazel` declares that
target. Each file can drift from the `.iml` it mirrors. Whoever finds the working invocation should
regenerate all four and delete their warning comments.

## Provenance statement

Reserved for T109. Every component in this fork is written from public specifications, public
documentation, and externally observable behaviour, as FR-001 requires. No component is derived from
a proprietary distribution.
