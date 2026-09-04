# Phase 0 Research: Remote Development (P1)

**Feature**: [spec.md](./spec.md) — User Story 1 only
**Date**: 2026-09-04
**Status**: Complete. No open NEEDS CLARIFICATION items.

This document records the decisions that Phase 1 depends on. Each decision gives the choice, the
reason, and the alternatives that we rejected. Every claim names the file that proves it.

---

## Summary of the repository survey

The first draft of the spec assumed that we build remote development from nothing. That assumption
is wrong. The Community Edition already contains most of the client and server split. The survey
found the parts below.

| Asset | Path | State |
|---|---|---|
| RPC transport | `fleet/rpc`, `fleet/rpc.server` | Complete. Apache 2.0 |
| Platform RPC service layer | `platform/kernel/rpc`, `rpc.backend`, `rpc.lite` | Complete. 81 files use `@Rpc` |
| Backend to frontend push | `platform/remote-topics` | Complete |
| Shared document model | `platform/kernel/pasta`, `fleet/andel` | Complete |
| Entity database | `fleet/rhizomedb`, `rhizomedb.transactor.rebase` | Complete |
| Per-client sessions | `platform/core-api/.../client/`, `ClientAwareComponentManager` | Complete. Only local sessions register |
| Mode flags | `platform/core-api/src/com/intellij/idea/AppMode.java`, `WellKnownCommand.java` | Command names reserved. No implementation |
| Product modes | `platform/runtime/product/src/ProductMode.java` | Complete |
| Split modules | 39 frontend and 68 backend `.iml` modules | Eight of ten FR-014 capabilities covered |
| UI seam | `platform/platform-impl/src/com/intellij/ui/split/` | Complete |
| Host access | `platform/eel`, `platform/eel-tcp`, `platform/ijent` | Complete for WSL and Docker. SSH descriptor present |
| Dev loop harness | `build/launch/src/com/intellij/tools/launch/ide/splitMode/` | Launches a backend and a frontend, including in Docker |

The parts below are absent and we must write them.

| Gap | Evidence |
|---|---|
| A backend `ApplicationStarter` | No `<appStarter>` implements `remoteDevHost`, `serverMode`, or `cwmHost` |
| Controller session registration | `ClientSessionManagerImpl` registers only local sessions |
| A connection and handshake layer | `remoteDev/ui/ConnectionManager.kt` is a 17-line interface with no implementation |
| A thin client product | No `JetBrainsClientProperties`. `MPSProperties.kt:53` says the root module is not in Community |
| An IJent agent binary | `IjentExecFileProvider` throws `IjentMissingBinary` |
| A refactoring split | No `refactoring.frontend` or `refactoring.backend` module |
| A find and replace frontend | `find.backend` exists with no matching frontend |

---

## D1. Session transport: use `fleet/rpc` with `platform/kernel/rpc`. Do not use Rd

**Decision.** Build the session protocol on `fleet/rpc` and expose services through
`platform/kernel/rpc`. Declare each service as an `@Rpc` interface and register it with the
`com.intellij.platform.rpc.backend.remoteApiProvider` extension point.

**Reason.** Three facts decide this.

1. The platform already made this choice. 81 files declare `@Rpc` interfaces. 25 XML files register
   a `remoteApiProvider`. Every split module uses `RemoteApiProviderService`, not Rd.
2. `fleet/rpc` supplies what FR-015 and FR-057 need. `fleet/rpc/srcCommonMain/fleet/rpc/core/ConnectionLoop.kt`
   holds a `ConnectionStatus` type with a `TemporarilyDisconnected` state and a retry delay strategy.
   `core/ProtocolVersion.kt` gives a version type. `core/RpcFlow.kt` gives streaming results.
   `server/ActiveConnections.kt` tracks each connected endpoint by socket and endpoint kind.
3. `fleet/rpc` is Apache 2.0 and its source is in this repository. We can change it.

**Alternatives rejected.**

*Rd.* `libraries/rd` holds no source. It is four wrapper modules that fetch the binary artifacts
`com.jetbrains.rd:*:2026.3.0`. We cannot patch it. Worse, Rd versions a protocol by an exact
`serializationHash` match on each generated model. A mismatch refuses to bind. Rd therefore detects
incompatibility but gives no negotiation and no backward compatibility. FR-057 requires both. Rd's
only over-the-wire use here is the distributed test harness, and its code generator runs from an
orphaned `platform/remoteDev-util/build.gradle` with undefined variables and no CI step.

*gRPC.* `libraries/grpc` exists, but its one consumer is `platform/execution-process-mediator`, a
local privileged process launcher. It is not an IDE transport.

**Work that remains.** `ProtocolVersion` is an 11-line data class with `current = "1"`. It is a slot
for a version, not a negotiation. FR-057 needs a real handshake on top of it.

---

## D2. Rendering: extend the existing split mode. Do not copy Lux

**Decision.** The backend runs headless and sends state. The thin client builds real Swing from that
state. Use `SplitComponentBinding` and the `com.intellij.frontend.splitComponentProvider` extension
point at `platform/platform-impl/src/com/intellij/ui/split/`.

**Reason.** The split already exists for eight of the ten capabilities that FR-014 lists. 39 frontend
modules and 68 backend modules are in the tree, under the Apache 2.0 licence. The approach also
matches the direction the platform itself is moving.

**Alternatives rejected.**

*Lux.* Lux runs real Swing on the backend with faked AWT peers, then ships the components. Community
holds only the client side of this. The shims are at `platform/platform-impl/src/com/intellij/platform/impl/toolkit/`.
The host lives in the closed-source package `com.jetbrains.rdserver.lux.*`. Proof is in the
hardcoded class-name allowlist at `platform/service-container/src/com/intellij/serviceContainer/ComponentManagerImpl.kt:1618-1640`.
To copy Lux, we must write that host from nothing. FR-001 forbids us to study the binary to do it.

**Cost that we accept.** A feature with no split renders nothing in the thin client. This makes the
split coverage the main scope driver for P1. See D7.

---

## D3. Session identity: reuse `ClientId` and `ClientSession`. Do not invent a session model

**Decision.** Represent each connected thin client as a `ClientSession` of `ClientType.CONTROLLER`.
Propagate identity with `ClientId`. Let per-client services resolve through `ClientAwareComponentManager`.

**Reason.** The model is complete and load-bearing. About 177 files reference `ClientId`. Coroutine
context propagation works and has a test at `platform/platform-tests/testSrc/com/intellij/util/concurrency/ClientIdPropagationTest.kt`.
Declarative per-client services already work through the `client="local"`, `client="remote"`, and
`client="all"` attributes. `ClientType.CONTROLLER` is documented as "a remote owner connected to the
IDE", which is exactly our thin client.

**Work that remains.** `ClientSessionManagerImpl` implements only `registerLocalSession()`. The
methods that register a controller session are the seam that the closed-source layer fills. We must
implement that seam.

---

## D4. Host access: reuse EEL and IJent. Write only the agent binary

**Decision.** Use EEL for every operation against the host. Use `EelExecApi` to start the backend,
`EelArchiveApi` to upload it, `EelTunnelsApi` for port forwarding, and the NIO integration for file
access. Use `SshEelDescriptor` for the SSH host kind.

**Reason.** EEL is one API over local, WSL, Docker, and SSH environments. It removes the need to
write a host abstraction. `MultiRoutingFileSystemProvider` in `platform/core-nio-fs` already routes
an NIO path to the correct backend, and it is installed on the boot classpath.

**Work that remains.** `IjentExecFileProvider.getIjentBinary()` throws `IjentMissingBinary`. Its
documentation says the binary arrives through a plugin. Community holds the client API and the
deployer, but not the agent. We must write our own agent and supply it through this interface.
FR-001 permits this, because the interface is public source in this repository.

---

## D5. Documents and reconnection: reuse `pasta` and `andel`

**Decision.** Represent a shared document with `platform/kernel/pasta`, which glues `fleet/andel`
to the IDE. Rely on its `EditLog` and its operation types to survive a disconnection.

**Reason.** FR-015 requires that no unsaved edit is lost across an outage of up to five minutes.
`fleet/andel/srcCommonMain/andel/operation/` holds `Operation`, `EditLog`, `CapturedOperation`, and
`NewOffsetProvider`. Composable operations with offset rebasing are the exact mechanism for replaying
a client edit against a newer server state. `fleet/rhizomedb.transactor.rebase` gives the same
property for entity writes. `platform/kernel/pasta` already exposes `DocumentEntity`,
`SharedDocumentInstructions`, and `LocalRangeMarker`.

This choice also lowers the cost of User Story 9 later, because concurrent editing needs the same
machinery.

---

## D6. Distribution: two products and a plugin build, all precedented

**Decision.** Build two products from the fork. The backend keeps the IDEA Community product and adds
a starter. The thin client becomes a new product with `ProductMode.FRONTEND`, attached to the empty
root module `platform/main/intellij.platform.frontend.main/`. Build the P2 to P10 plugin channel with
the standalone pattern in `build/src/KotlinPluginBuildTarget.kt`.

**Reason.** Four products already build from this repository. They are IDEA Community, PyCharm
Community, MPS, and Android Studio. `ProductProperties` and the Product DSL give a documented path.
`ProductProperties.rootModuleForModularLoader` and `ProductProperties.productMode` already support a
module-based product. The root module shells exist at `platform/main/` with no sources.

**Constraints that the build imposes.**

- If a product sets `rootModuleForModularLoader`, then `productLayout.bundledPluginModules` must be
  empty. Bundled plugins must move to a `product-modules.xml`. See `BuildTasksImpl.kt:563-573`.
- A plugin does not declare its compatible platform range in source. `PluginXmlPatcher.kt` injects
  `since-build` and `until-build` at build time from a `CompatibleBuildRange`. FR-055 is therefore a
  build configuration choice, not a source change.
- The native launcher is Rust at `native/XPlatLauncher`. The product build downloads a prebuilt
  binary and does not compile it. A new product reuses that launcher and stamps its own icon.

**Alternative rejected.** A single product that switches mode at run time. This fails because a thin
client must stay small, and because the two products need different icons, different installers, and
different bundled content.

---

## D7. P1 scope driver: the split coverage of FR-014

**Decision.** Treat the two missing splits as P1 work. Treat every other capability as integration
and test work, not as new architecture.

**Reason.** FR-014 lists ten capabilities. The survey mapped each one to the modules that exist.

| Capability | Modules | Verdict |
|---|---|---|
| Editing | `editor.frontend`, `editor.backend` | Present |
| Completion | `completion.frontend`, `completion.backend` | Present |
| Navigation | `navbar`, `structureView`, `recentFiles` | Present |
| Search | `searchEverywhere`, `searchEverywhereLucene` | Present |
| Refactoring | none | **Absent** |
| Version control | `vcs.impl.frontend`, `vcs.git.frontend`, `vcs.git.backend` | Present |
| Run | `execution.impl`, `execution.dashboard`, `execution.serviceView` | Present |
| Debug | `debugger.impl.frontend`, `debugger.impl.backend` | Present |
| Test execution | rides on the execution modules | Partial |
| Terminal | `terminal.frontend`, `terminal.backend` | Present |

Find and replace also lacks a frontend. `find.backend` exists alone.

---

## D8. Development loop

**Decision.** Adapt `build/launch/src/com/intellij/tools/launch/ide/splitMode/` into the day-to-day
loop. Do not expect to use it unchanged.

**Reason.** It already launches a backend and a frontend as two processes, and it can run the backend
in Docker. `IdeLauncher.kt`, `IdeBackend.kt`, and `IdeFrontend.kt` hold the logic. That removes the
need to build installers to test a change.

**Correction, found during implementation.** The harness cannot be used as it stands, and the first
version of this decision overstated it.

- It exposes no entry point. `launchIde` is a suspend extension on `CoroutineScope` that takes a DSL
  builder, `IdeLauncher` is a `private object`, and `build/launch/BUILD.bazel` declares no
  `java_binary`.
- It is hardwired to the proprietary client. `IdeFrontend.kt:43` passes
  `platformPrefix = IdeConstants.JETBRAINS_CLIENT_PREFIX`, the string `JetBrainsClient`, and
  `IdeConstants` also names `intellij.gateway.plugin`, which is absent from this repository.
- FR-052 forbids depending on that client, so the harness must be parameterised to take our own
  platform prefix. That prefix does not exist until `ThinClientProperties` lands.

The work is therefore larger than a Bazel target, and it must follow the thin client product rather
than precede it. Tracked at T021.

---

## D9. Security

**Decision.** Terminate TLS at the backend. Bind the backend to loopback on the host and reach it
through an EEL tunnel. Store every credential through the platform credential store. Make the
connection token revocable per session.

**Reason.** FR-018 requires encryption in transit and revocable credentials. FR-008 forbids a secret
in a log or a version-controlled file. An EEL tunnel means that the session port is never open on a
public interface, which removes a class of exposure without extra code. `OsRegistryConfigProvider`
at `platform/remoteDev-util/` already reads administrator-managed settings from the Windows registry
and from `/etc/xdg/JetBrains/`, so an administrator can pin our endpoints.

**Note on the reserved names.** `SocketWire` in Rd carries an `allowRemoteConnections` flag, and the
test harness sets it. We do not reuse that path. Our transport is `fleet/rpc`.
