# Fork Platform Changes

This file is the ledger that constitution v1.0.0 Principle 1, Extension Point First, requires. It
also serves FR-054, which states that the fork must stay rebasable on upstream Community Edition.

Every change to a file that upstream also maintains gets one row here. The row states why the change
could not be made in fork-owned code. A change with no row is a review failure.

**Current count of changed upstream source files: 4. Planned: 0.**

Eight tracked files differ from upstream in total. Four are the hand-edited source changes counted
above. The other four are registry or generator output, listed in the next section, and they are
excluded because nobody chooses their contents.

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
- `platform/main/intellij.platform.backend.main/BUILD.bazel` and the matching
  `intellij.platform.frontend.main/BUILD.bazel` ... generator output for the two `.iml` edits that
  are counted. `jpsModelToBazelCommunityOnly.cmd` writes them. Editing them by hand is a mistake:
  the next generator run reverts it.

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

- **Rebase note**: the T112 drill measured 6 upstream commits on this file in twelve months, and
  none between the fork's base and `idea/2026.3-eap-1`. The change is one entry appended to a map
  literal, away from the lines upstream edits. The file does not exist before `idea/2026.1`, so a
  rebase onto anything older is not a conflict but a redesign.

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

## Rebase drill, T112

Run on 2026-09-05 against `idea/2026.3-eap-1`, the newest upstream tag, from a fork based on master
at `fa9f6fa1e617`.

### The instrument matters

`git merge-tree HEAD idea/2026.3-eap-1` reports 195 conflicting paths. That number is not this
fork's rebase cost, and quoting it would be wrong. Master and a release branch have diverged on their
own, and almost every one of those paths is upstream against upstream, in files this fork has never
touched.

The question SC-017 asks is narrower: do the fork's own platform modifications still apply? So the
drill merges each changed file on its own, three ways, from the fork's base to the release tag. Eight
files, eight merges.

### Result

| Release tag | Clean | Conflicting | Not yet present |
| --- | --- | --- | --- |
| `idea/2026.3-eap-1` | 8 | 0 | 0 |
| `idea/2026.2.2` | 8 | 0 | 0 |
| `idea/2026.2` | 8 | 0 | 0 |
| `idea/2026.1` | 5 | 2 | 1 |
| `idea/2025.3` | 3 | 4 | 1 |

No new file this fork adds collides with a path that already exists at the tag, so nothing conflicts
on addition.

The two older tags are the drill run backwards, onto releases that predate the platform structure
these changes assume. They are not a forward rebase and they are not a failure. They are here because
they show which files are fragile once upstream has really moved.

### What the drill found

The fragile files are not the ones churn predicts, and the inversion is the useful part.

`.idea/modules.xml` took 654 upstream commits in twelve months and
`build/bazel-generated-file-list.txt` took 556, roughly twice a day each. Both merged clean at every
tag. They are long append-only registries, and this fork's entries sit nowhere near the lines
upstream keeps editing.

The two `main/BUILD.bazel` files took 12 and 10 commits in the same period, two orders of magnitude
less, and both conflicted at `idea/2026.1` and at `idea/2025.3`. They are short, and the edit adds a
dependency inside the one `deps` list upstream also reorders.

So the predictor of rebase pain is where an edit lands, not how often the file changes. A one-line
addition to the end of a thousand-line registry is nearly free. The same addition inside the only
list in a forty-line file is not. A future change should be judged that way.

### Against SC-017

SC-017 allows ten working days to rebase onto an upstream release. The three most recent tags need
zero conflict resolution, and the worst case measured, a backwards rebase across two major releases,
conflicts in four small files whose resolution is mechanical. The target holds with room to spare.

The result to watch is not the conflict count. It is the count of changed source files, which is 4.
Each new one adds a permanent re-argue cost at every release, and that is what this ledger exists to
hold down.

## Provenance statement

Every component in this fork is written from public specifications, public documentation, and the
source in this repository, as FR-001 requires. No component is derived from a proprietary
distribution.

In particular, during this work nobody decompiled, disassembled, or read the bytes of a proprietary
build, and nobody recovered a wire format by watching a proprietary client talk to a proprietary
host. The session protocol in `platform/remoteDev-protocol` is specified in
`specs/001-ultimate-feature-parity/contracts/session-protocol.md` and was designed there first. It is
not a copy of, and does not interoperate with, any proprietary protocol.

Two decisions record the discipline rather than assert it, because a rejected shortcut is better
evidence than a promise:

`libraries/rd` was rejected. It is available, and it would have saved work. It ships as compiled
Maven artifacts, and it identifies a version by an exact `serializationHash`, so it can tell that two
peers disagree but cannot negotiate between them. FR-057 needs negotiation. Research decision D4.

The Lux host was rejected. It is the obvious model for a split host, and it is closed source
(`com.jetbrains.rdserver.*`). Community ships only the client-side toolkit shims. Reading it was
never an option, so the backend was designed from the contract instead. Research decision D5.

### What the automated checks do and do not prove

`build/thin-client/src/LicenseValidator.kt` and `SecretScanner.kt` run over the built distribution
through `//build/thin-client:verify-artifacts`. On the last run they reported 133 components, every
licence on the allowed list, and no credential in any shipped file.

The licence check uses an allowed list, not a denied list. An unrecognised licence stops the build
and asks a person, which is why the first real run surfaced five licences the list had missed rather
than passing them in silence.

These checks prove that the distribution ships nothing under an incompatible licence and no
credential. They do not, and cannot, prove authorship. The claim above rests on how the work was
done, and the record of it is this repository's history and the research decisions in
`specs/001-ultimate-feature-parity/research.md`.

## Known tensions

`AdminHostPolicy.forProduct` reads administrator settings through the platform's
`OsRegistryConfigProvider`. That class writes its paths as `SOFTWARE\JetBrains\<product>` on
Windows and `JetBrains/<product>` under `/etc/xdg`, and the segment is not a parameter.

So a de-branded fork reads its administrator configuration from a path carrying the JetBrains name,
which sits badly with FR-002. The alternatives are worse: changing the platform class would make it
the fifth upstream file this fork edits, and copying it would duplicate the registry, XDG, and
Application Support handling for the sake of one path segment.

This is recorded, not resolved. Whoever decides FR-002's exact boundary should decide it here.
