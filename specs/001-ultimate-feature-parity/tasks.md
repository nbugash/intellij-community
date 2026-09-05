---
description: "Task list for Remote Development (User Story 1, P1)"
---

# Tasks: Remote Development (P1)

**Input**: Design documents from `specs/001-ultimate-feature-parity/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/session-protocol.md](./contracts/session-protocol.md),
[quickstart.md](./quickstart.md)

**Scope**: User Story 1 only. The spec holds ten user stories. Its Scope Warning requires one plan and
one task list for each slice. User Story 2 to User Story 10 are out of scope here.

**Tests**: Included. Three sources require them. The repository `AGENTS.md` mandates
`./tests.cmd` after a code change. `quickstart.md` already defines the eight acceptance checks. The
success criteria SC-002, SC-003, and SC-018 state measurements that only a test can produce.

**Constitution**: `.specify/memory/constitution.md` is ratified at v2.0.0, a five-principle set.
Principle 5, Testing, governs the testing rule below. Principle 4, Threading, and Principle 2, Clean
Code, each exposed a gap that T120 and T123 close. Principle 3, Resource Ceiling, binds every Bazel
invocation in this list: measure free memory first, pass an explicit `--local_ram_resources` cap, and
prefer a narrow target pattern over a broad one.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel. Different files, no dependency on an incomplete task
- **[US1]**: Belongs to User Story 1
- Every task names an exact file path

## Repository conventions that every task must respect

These come from `AGENTS.md` and they override general practice.

- `*.iml` files are the source of truth. They generate `BUILD.bazel`. Never hand-edit a generated
  `BUILD.bazel` and never hand-edit `.idea/modules.xml`.
- Register a new `.iml` with `bun build/jps-module.mjs register <path> --fix-iml-eof`, then run
  `./build/jpsModelToBazelCommunityOnly.cmd`.
- Every user-visible string belongs in a `*.properties` file.
- Run affected tests with `./tests.cmd --module <module> --test <FQN>`. A simple class name matches
  nothing.
- Write every comment, KDoc, and commit message in Simplified Technical English.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the module skeletons and prove the build model stays consistent.

- [X] T001 Record the baseline in `specs/001-ultimate-feature-parity/baseline.txt`. **Reworded under constitution Principle 3, Resource Ceiling**: the original `bazel build //platform/...` is the broad target pattern the principle names as the most likely way to breach the memory ceiling. Build only the modules under change, measure `free -g` first, and pass `--local_ram_resources`. A targeted build of `//platform/kernel/rpc:rpc` succeeded in 348 s and proves the toolchain
- [X] T002 [P] Create the protocol module descriptor at `platform/remoteDev-protocol/intellij.platform.remoteDev.protocol.iml`
- [X] T003 [P] Create the backend module descriptor at `platform/remoteDev-backend/intellij.platform.remoteDev.backend.iml`
- [X] T004 [P] Create the frontend module descriptor at `platform/remoteDev-frontend/intellij.platform.remoteDev.frontend.iml`
- [X] T005 [P] Create the provisioning module descriptor at `platform/remoteDev-provisioning/intellij.platform.remoteDev.provisioning.iml`
- [X] T006 [P] Create the agent module descriptor at `platform/ijent-agent/intellij.platform.ijent.agent.iml`
- [X] T007 [P] Create the four test module descriptors: `platform/remoteDev-protocol/intellij.platform.remoteDev.protocol.tests.iml`, `platform/remoteDev-backend/intellij.platform.remoteDev.backend.tests.iml`, `platform/remoteDev-frontend/intellij.platform.remoteDev.frontend.tests.iml`, `platform/remoteDev-provisioning/intellij.platform.remoteDev.provisioning.tests.iml`
- [X] T008 Register every new descriptor with `bun build/jps-module.mjs register <path> --fix-iml-eof` for each `.iml` created in T002 to T007
- [X] T009 Regenerate the Bazel model with `./build/jpsModelToBazelCommunityOnly.cmd`. Unblocked by running `./getPlugins.sh --shallow`, which clones the android modules that `intellij.gradle.analysis` depends on. The generator now completes and emits `BUILD.bazel` for all five new modules
- [X] T010 Verify the model. Three checks, all passing. (a) `bazel build` over all five modules' `:all` patterns: 40 targets, 270 s, exit 0. (b) `./build/assertJpsModelToBazelCommunityPaths.sh`: whole-repo build of 14,657 targets, exit 0, no error naming a new module. (c) `bazel run //:format.check`: no diff. All run under constitution Principle 3, Resource Ceiling: memory measured first, `'--local_resources=memory=HOST_RAM*.75'` passed, peak 47% of 61 GiB, inside the 75% ceiling
- [X] T011 [P] Create the message bundle at `platform/remoteDev-protocol/resources/messages/RemoteDevProtocolBundle.properties` with the eight failure messages from the session contract, and its accessor at `platform/remoteDev-protocol/src/RemoteDevProtocolBundle.kt`. Note the flat source layout: the `.iml` declares `packagePrefix`, which is the convention in newer modules such as `platform/eel-tcp`. Verified compiled: `remoteDev-protocol.jar` contains `com/intellij/remoteDev/protocol/RemoteDevProtocolBundle.class` and `messages/RemoteDevProtocolBundle.properties`
- [X] T012 [P] Create the fork change ledger at `docs/fork-platform-changes.md` with one row per changed upstream file, as FR-054 requires

**Checkpoint**: Five source modules and four test modules exist. The JPS model and the Bazel model agree.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The wire types and the two product shells. Both products depend on these, so nothing in
User Story 1 can start until this phase completes.

**CRITICAL**: No User Story 1 task may begin before T026 passes.

- [X] T013 [P] Define the serializable session types from [data-model.md](./data-model.md) in `platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/SessionModels.kt`
- [X] T014 [P] Define `ProtocolVersion` with a total ordering and a supported-range rule in `platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/ProtocolVersion.kt`, per contract section 3.3
- [X] T015 [P] Define the failure code enum from contract section 6 in `platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/SessionFailure.kt`, with a message key and a next action for every code
- [X] T016 Define the handshake offer, accept, and refuse messages in `platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/ProtocolHandshake.kt` (depends on T013, T014, T015)
- [X] T017 Declare the `@Rpc` session service interfaces in `platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/SessionApi.kt` (depends on T013)
- [X] T018 [P] Add `platform/remoteDev-protocol/module-content.yaml`. **Task text corrected**: this file declares the distribution jar layout, not dependencies. Dependencies live in the `.iml`. Written as a `dist.all/lib/intellij.platform.remoteDev.protocol.jar` entry
- [X] T019 Attach the backend product at `platform/main/intellij.platform.backend.main/intellij.platform.backend.main.iml`. **The shell is not empty**, contrary to the plan: it already aggregates 25 runtime dependencies. One `orderEntry` added. Recorded in `docs/fork-platform-changes.md` as an upstream change
- [X] T020 Attach the thin client product at `platform/main/intellij.platform.frontend.main/intellij.platform.frontend.main.iml`. The shell already aggregates 16 runtime dependencies. One `orderEntry` added. Recorded in `docs/fork-platform-changes.md`
- [ ] T021 **BLOCKED by T039 to T046, and larger than the plan assumed.** Add a runnable split-mode target named `ide-split-mode` to `build/launch/BUILD.bazel`. Three findings. (a) There is no entry point: `launchIde` is a suspend extension on `CoroutineScope` taking a DSL builder, `IdeLauncher` is a `private object`, and `build/launch/BUILD.bazel` declares no `java_binary`. (b) The harness is hardwired to the proprietary client: `IdeFrontend.kt:43` passes `platformPrefix = IdeConstants.JETBRAINS_CLIENT_PREFIX`, which is the string `JetBrainsClient`, and `IdeConstants` also names `intellij.gateway.plugin`, a module absent from this repository. (c) FR-052 forbids depending on that client, so the harness must be parameterised to accept our own platform prefix, which only exists once `ThinClientProperties` lands at T039. Sequence this after T046
- [ ] T022 **DEFERRED to P1.1, merge with T038.** Create the latency measurement harness at `platform/remoteDev-protocol/testSrc/com/intellij/remoteDev/protocol/LatencyHarness.kt`. No transport exists until P1.1, so a harness built now would measure nothing and would be speculative scaffolding, which constitution Principle 2, Clean Code, forbids under YAGNI. The plan's reason for early measurement was to fix the message shape before it is expensive to change; the shape is now fixed by T016, so the harness must land with T038 and re-open T016 if the measurement demands it
- [X] T023 [P] Write the failing handshake contract test at `platform/remoteDev-protocol/testSrc/com/intellij/remoteDev/protocol/HandshakeTest.kt` covering accept, refuse, and the rule that a refusal precedes any project open
- [X] T024 [P] Write the failing version negotiation test at `platform/remoteDev-protocol/testSrc/com/intellij/remoteDev/protocol/VersionNegotiationTest.kt` covering SC-018
- [X] T025 Red then green, both observed. RED: building the test target before any type existed reported `Unresolved reference` for `ProtocolVersion`, `ProtocolVersions`, `ClientOffer`, `SessionToken`. GREEN after T013 to T017: `./tests.cmd --module intellij.platform.remoteDev.protocol.tests --test 'com.intellij.remoteDev.protocol.*'` reports **14 tests, 14 passed, 0 failed** in 229 ms
- [X] T026 Regenerate Bazel and build. `bazel build //platform/remoteDev-protocol/...` completes successfully. Required one dependency the plan did not foresee: `intellij.platform.kernel`. Without it the fleet RPC compiler plugin aborts with `IrGenerationExtensionException: List is empty` on any `@Rpc` interface. Causation proven by removing it and reproducing the failure. `fleet.rhizomedb`, which other `@Rpc` modules carry, is NOT required

**Checkpoint**: The wire contract compiles, its tests fail as intended, and both product shells are attached.

---

## Phase 3: User Story 1 - Remote Development (Priority: P1) 🎯 MVP

**Goal**: A developer opens a project on a remote machine from a local thin client. The IDE runs on
the remote machine. The local machine never holds the sources.

**Independent Test**: Provision a remote host over SSH, open a project of at least 50,000 files, edit
a file, trigger completion, run the test suite, and set a breakpoint, with no copy of the sources on
the local machine.

### P1.1 Backend starter and controller session (FR-011 local form)

- [X] T027 [US1] Failing test written first at `platform/remoteDev-backend/testSrc/com/intellij/remoteDev/backend/BackendDirectoryLayoutTest.kt`. **Scope narrowed with reason**: asserting that a starter opens a project and stays alive needs a running application fixture, which belongs to the end-to-end suite, not a unit test. The unit-testable rule here is the FR-019 directory isolation, which is pure logic and where a real defect can hide. RED observed: `Unresolved reference 'BackendDirectoryLayout'`. GREEN after T036: 7 tests, 7 passed
- [X] T028 [US1] Implement the long-lived headless starter at `platform/remoteDev-backend/src/com/intellij/remoteDev/backend/RemoteDevHostStarter.kt`, modelled on the structure of `plugins/mcp-server/src/com/intellij/mcpserver/McpServerHeadlessStarter.kt`
- [X] T029 [US1] Register the starter with an `<appStarter>` entry in `platform/remoteDev-backend/resources/intellij.platform.remoteDev.backend.xml`
- [X] T030 [US1] Wire the command in `platform/core-api/src/com/intellij/idea/WellKnownCommand.java`. Added `put("splitBackend", HEADLESS_REMOTE_DEV_HOST)`, 3 lines including the comment. **The plan assumed a reserved name could be reused. It cannot**: every reserved remote name maps to `REMOTE_DEV_HOST`, which is `isHeadless=false`, the Lux design. Our split-mode backend is headless, so it needs `HEADLESS_REMOTE_DEV_HOST`, a constant that already exists and is already used by `provisionTbeBackend`
- [X] T031 [US1] `ControllerSession.kt` holds `ControllerAppSession`, extending `ClientAppSessionImpl` with `ClientType.CONTROLLER`, and `ControllerSessionRegistrar`, which calls the public `registerSession`. **Compiled, not runtime-tested**: exercising it needs a running application fixture, which belongs to the end-to-end suite. Subclassing `ClientAppSessionImpl` pulled two further dependencies, `intellij.platform.serviceContainer` and `intellij.platform.projectModel.impl`, taking the module from 9 to 14
- [X] T032 [US1] **NO CHANGE REQUIRED.** T122 established that `platform/platform-impl/src/com/intellij/openapi/client/ClientSessionManagerImpl.kt` does not need modification. `ClientSessionsManager.registerSession` is public, `ClientAppSession` and `ClientProjectSession` are interfaces, and the session managers are services that accept `overrides="true"`. Register the controller session from `ControllerSessionRegistrar.kt` at T031 instead. Recorded in `docs/fork-platform-changes.md`
- [X] T033 [US1] `BackendSessionRegistry.kt`, an application service recording which session holds which project, so a second client is refused with `PROJECT_LOCKED`. **Scope split with reason**: the loopback binding is transport, which arrives in P1.3 with the wire. Naming it a registry rather than a service states what it actually is
- [X] T034 [US1] `BackendSessionApi` implements `SessionApi` over `HandshakeResponder`, published by `BackendSessionApiProvider` through `platform.rpc.backend.remoteApiProvider` in `platform/remoteDev-backend/resources/intellij.platform.remoteDev.backend.xml`. **Token validation fails closed**: `isTokenValid` returns false until T103 wires the credential store, because accepting an unverified credential would defeat FR-018
- [X] T035 [US1] `BackendTrustGate.kt` with `BackendOperation`, classifying each operation by whether it runs project-supplied code. An untrusted project refuses every code-running operation and allows every read-only one, so a user can still inspect a project before deciding to trust it. 4 tests cover FR-007 and SC-015
- [X] T036 [US1] Implement per-backend configuration and system directory isolation in `platform/remoteDev-backend/src/com/intellij/remoteDev/backend/BackendDirectoryLayout.kt`, so that FR-019 holds for several backends on one host
- [X] T037 [US1] `./tests.cmd --module intellij.platform.remoteDev.backend.tests --test 'com.intellij.remoteDev.backend.*'` reports **7 tests, 7 passed, 0 failed** in 156 ms. Note the module name takes the `.tests` suffix
- [ ] T038 [US1] Measure keystroke and completion latency with the T022 harness at 0 ms and at 100 ms induced delay, and record the result in `specs/001-ultimate-feature-parity/latency-baseline.md`

**Note on T038**: [plan.md](./plan.md) records this as a risk. Measure before P1.3 fixes the message
shape, because a protocol change is cheap early and expensive late.

### P1.2 Thin client product and installers

- [X] T039 [P] [US1] `ThinClientProperties` at `build/thin-client/src/ThinClientProperties.kt`. **Two deviations.** It extends `ProductProperties`, not `JetBrainsProductProperties`, because that subclass applies JetBrains branding and FR-002 forbids presenting this as a JetBrains product. It lives in a new module `intellij.idea.community.build.thinClient`, not in `intellij.idea.community.build`, so that adding it needs no upstream change. PyCharm does the same from `python/build`
- [X] T040 [P] [US1] `platform/remoteDev-frontend/resources/idea/ThinClientApplicationInfo.xml`. **Placed with the product's own module rather than in `community-resources`**, so the product identity travels with the product. `names@script` is `remote-client`, equal to `baseFileName`. The vendor is the fork, not JetBrains, per FR-002
- [X] T041 [P] [US1] Eight mandatory images under `thin-client-images/`. **Crude placeholders, generated on request, not final art**: a flat slate ground with an 'RC' mark. Every file is a real image in the right format and size, verified with `file`: 128x128 PNG, three 16x16 ICO, 150x57 and 164x314 BMP, a TIFF, and a hand-built ICNS carrying ic07/ic08/ic09 PNG chunks. ImageMagick writes a PNG when asked for `.icns`, so the ICNS container was written directly and its magic and declared length checked
- [X] T042 [US1] Move the bundled plugin list into `platform/remoteDev-frontend/resources/META-INF/product-modules.xml`, because a product that sets `rootModuleForModularLoader` must leave `productLayout.bundledPluginModules` empty
- [X] T043 [US1] Create the build target main at `build/src/ThinClientInstallersBuildTarget.kt` (depends on T039)
- [X] T044 [US1] `java_binary` named `i_build_target` in `build/thin-client/BUILD.bazel`, hand-written above the auto-generated sections, following `python/build/BUILD.bazel`. **Not added to `build/BUILD.bazel`**, which is an upstream file. Verified that it survives `jpsModelToBazelCommunityOnly.cmd`
- [X] T045 [P] [US1] `thin-client-installers.cmd` at the repository root, pointing at `@community//build/thin-client:i_build_target`
- [X] T046 [US1] Register the product in `build/dev-build.json`. **My earlier deferral was wrong.** I called it optional because PyCharm Community and MPS ship installers without appearing in it. That file is `PRODUCT_REGISTRY_PATH`, which the Product DSL generator reads to discover a product and emit its descriptors. 7 lines. This is the fourth changed upstream file
- [X] T047 [US1] `FrontendSessionController.kt` drives the contract section 4 state machine through the shared `SessionLifecycle`, so the client cannot invent a state the host would reject. `HostLink.kt` parses and validates a connection link, refusing another product's scheme per FR-052 and carrying no credential. 11 tests
- [ ] T048 [US1] **DEFERRED. The task's premise does not hold.** `ConnectionManager` lives in `intellij.remoteDev.util`, which carries 47 dependencies and pulls `intellij.platform.testFramework.junit5` into production scope. Depending on it produced a real Bazel cycle: `ERROR: cycle in dependency graph` through `//platform/testFramework/junit5`. Constitution Principle 3, Architecture, forbids a cycle and forbids hiding one behind a runtime lookup. The interface is 17 lines, `@ApiStatus.Experimental`, and has no implementation anywhere in the repository. Declare our own connection contract in `platform/remoteDev-frontend` when the transport lands in P1.3, rather than taking that dependency
- [ ] T049 [US1] **DEFERRED to the end-to-end suite.** Assert that the client holds no project source. Proving an absence needs a running client with an open remote session, so a unit test cannot state it. Belongs with the `quickstart.md` Check 1 run, where the client process and its directories can be inspected
- [X] T050 [US1] **Installers built and verified.** `remoteClient-263.SNAPSHOT.tar.gz` 230 MB and `remoteClient-263.SNAPSHOT-aarch64.tar.gz` 229 MB under `out/remote development client/artifacts/`, both `sha256sum -c` OK, zero error spans in the build trace. Verified by extraction, not by exit code: the shipped jars contain `ClientOffer`, `HandshakeReply`, `ProtocolVersion`, `SessionApi`, `FrontendSessionController`, `HostLink` and both message bundles. `product-info.json` reports `productCode=RC`, `dataDirectoryName=RemoteClient2026.3`, `launcherPath=bin/remote-client`, `mainClass=IntellijLoader`, and `productVendor=IntelliJ Community Remote Development` per FR-002. **Eleven attempts.** The wrapper exited 0 on every failure, so never trust it: check the trace for error spans and open the artifact. Only the current OS was built; the other four operating system and architecture pairs remain

**Checkpoint (MVP-0)**: The local split runs. `bazel run //build/launch:ide-split-mode` starts a
backend and a client, and the client shows the project. No remote machine is needed. This is the
earliest demonstrable point.

### P1.3 Session protocol and version negotiation (FR-057, SC-018)

- [X] T051 [US1] The handshake exchange is `HandshakeResponder.kt`, delivered in P1.1. **Placed in the backend module, not the protocol module**: answering an offer is host behaviour and depends on host state, while the protocol module holds contracts only. Putting it in the protocol module would point a dependency outward, which constitution Principle 1, Clean Architecture, forbids. T023 passes
- [X] T052 [US1] `VersionNegotiator` in `platform/remoteDev-protocol/src/ProtocolVersion.kt`, delivered in Phase 2. Takes the first offered version the host supports, per contract 3.3, so the client's order decides. T024 passes
- [X] T053 [US1] `ProtocolVersions.supportedBy` implements the FR-057 rule. Covered by two tests in `VersionNegotiationTest.kt` rather than a separate `SupportedRangeTest.kt`, because they exercise the same object
- [X] T054 [US1] Covered in `HandshakeResponderTest.kt` by two tests that use a recording policy to assert the project was never looked up when the host refuses on version or credential. Kept beside the other handshake tests rather than in a separate `RefusalOrderTest.kt`
- [X] T055 [US1] Implement length-prefixed framing and the `kotlinx.serialization` payload encoding at `platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/SessionFraming.kt`
- [X] T056 [US1] Implement the failure model so that every refusal carries a code, a user message, and a next action, at `platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/SessionFailureReporter.kt`, per FR-010
- [X] T057 [US1] Covered by `a refusal never carries the offered token` in `HandshakeResponderTest.kt` and by `no failure text carries the session token` in `SessionFailureReporterTest.kt`, which drives every failure code
- [X] T058 [US1] Add the contract change rules as an enforced test at `platform/remoteDev-protocol/testSrc/com/intellij/remoteDev/protocol/ContractEvolutionTest.kt`, asserting that a new field is optional
- [X] T059 [US1] `./tests.cmd --module intellij.platform.remoteDev.protocol.tests --test 'com.intellij.remoteDev.protocol.*'` reports **32 tests, 32 passed**. The backend module reports **18 tests, 18 passed**. 50 tests across the slice

### P1.4 Provisioning over EEL and IJent (FR-012, FR-013, SC-001)

- [X] T060 [P] [US1] Failing tests written first: `HostRegistryTest.kt` and `AgentDeploymentTest.kt`. RED observed as unresolved references to every type. **Scope narrowed with reason**: a test that provisions a real SSH host is an end-to-end test, not a unit test. The unit-testable rules are the credential handling and the deployment state machine, which is where a real defect can hide
- [X] T061 [P] [US1] `AgentDeploymentTest.kt`, placed in the provisioning module rather than `ijent-agent`, because deployment is what the provisioner does and the agent module holds the binary. Covers idempotence, clean failure back to ABSENT, and the rule that a deployment never reaches READY without passing through VERIFYING
- [X] T062 [US1] `HostRegistry.kt` with `HostId`, `CredentialRef` and `HostRecord`. `CredentialRef.toString` is redacted, so a record that holds one cannot print it by accident. A test asserts the reference never appears in a record's text
- [X] T063 [US1] `AgentSelection.kt` holds `HostOs`, `HostArch` and `HostPlatform`. The type carries **no default and no client fallback**, so a caller must supply what it read from the host. A developer on macOS routinely drives a Linux host, and a guess would deploy a binary that cannot execute. Reading the value from `EelPlatformApi` is the adapter work in T068, which is where EEL enters
- [X] T064 [US1] **WITHDRAWN.** Write our own agent binary. Blocked and unnecessary. Blocked: the agent serves gRPC, and no `.proto` or generated stub for IJent exists in this repository, so a compatible agent cannot be written, and recovering the protocol from the proprietary binary is what FR-001 forbids. Unnecessary: IJent lets a **local** IDE reach a remote machine, and research decision D2 put the IDE on the host, so the backend already reads files and starts processes locally. See decision D10
- [X] T065 [US1] **WITHDRAWN with T064.** Implement the `IjentExecFileProvider` supplier. There is no agent binary to supply. The `platform/ijent-agent` module stays as a scaffold in case decision D10 is revisited
- [X] T066 [US1] `HostBootstrap.upload` is the upload step of the bootstrap contract in `HostBootstrap.kt`. **Integrity verification moved**: a resident agent is no longer deployed, so there is no agent binary to verify. The distribution's own checksum covers the transfer, and `AgentDeployment` keeps the verify-before-run rule for the day decision D10 is revisited
- [X] T067 [US1] `AgentDeployment.kt` holds the state machine. Two rules carry the weight: a deployment never moves from UPLOADING straight to READY, so an altered or truncated binary is never executed; and a failure returns to ABSENT rather than resting half-deployed, so a retry starts clean per FR-009
- [X] T068 [US1] `HostProvisioner.bootstrap` runs upload, then start, then forward, and returns the local port. The start command is `splitBackend --project=<path>`, which matches the application starter registered at T029 and the command name added at T030. Tests assert the order, the command, and the returned port
- [X] T069 [P] [US1] SSH host kind routed to `BootstrapRoute.SHELL_OVER_SSH`. The execution environment layer routes SSH through IJent, which decision D10 rules out, so this kind uses a shell session directly. The concrete shell adapter is P1.4 follow-up work
- [X] T070 [P] [US1] Linux-on-Windows host kind routed to `BootstrapRoute.EXECUTION_ENVIRONMENT_LAYER`, which supports it today with no agent of ours
- [X] T071 [P] [US1] Container host kind routed to `BootstrapRoute.EXECUTION_ENVIRONMENT_LAYER`, as for the Linux-on-Windows kind
- [X] T072 [US1] `HostProvisioner.bootstrap` reports each step through a progress callback and checks for cancellation before each one. A failure raises `BootstrapException` naming the step, and stops the remaining steps, so a retry never starts from a half-provisioned host. Tests cover progress, the named failure, and the stop
- [X] T073 [US1] `FirstRunBudget` holds the SC-001 limit of ten minutes, splits it across the bootstrap steps, and judges a measured run. **The end-to-end number still needs a real host**, and `quickstart.md` Check 3 holds that. What is enforced on every build: the step shares add up to the limit, so raising one without lowering another fails the build rather than SC-001; a breach is reported with the measured number, not a verdict alone; and a step with no measurement fails the run, because counting it as zero would let an unmeasured run claim success
- [X] T074 [US1] Covered in `AgentSelectionTest.kt`. `AgentSelection.mismatch` refuses an agent built for another operating system or another architecture, and its text names both platforms, because FR-010 requires a next action and "wrong platform" alone tells the user nothing
- [ ] T075 [US1] **DEFERRED, and the reason is the cost.** `build/launch/BUILD.bazel` is an upstream file, so this target would make it the fifth this fork changes, against four today. The check it serves is the end-to-end provisioning run, which is T099, and T099 needs a remote host and is itself deferred. So the permanent re-argue cost lands now and the benefit does not. Whoever picks this up should put the target in `build/thin-client/BUILD.bazel`, which the fork owns outright, unless something in the launcher rules genuinely requires the upstream package; nothing found so far does

### P1.5 Reconnection and unsaved state (FR-015, SC-003)

- [X] T076 [US1] `ReconnectionTest.kt` drives **100 induced interruptions** with a seeded random clock, per SC-003. Each round drops the connection at a random moment inside the window, writes one to four edits, then reconnects. The test asserts that every edit written is replayed, and replayed in order
- [X] T077 [US1] `SessionRetention` in the protocol module holds a session whose connection dropped, together with its unsaved edits, for the retention window. Placed in the protocol module rather than the backend, because the client also needs the window to decide whether to attempt a resume
- [X] T078 [US1] `SessionReconnector` drives the session lifecycle from the transport and buffers the edits made while it is down. **It does not reconnect**: `fleet/rpc`'s connection loop already retries with exponential backoff between 1 ms and 30 s, so re-implementing that would duplicate it. What this adds is FR-015's other half, that the session keeps its identity and its edits are replayed. `TransportState` is a small enum of our own rather than `fleet.rpc.ConnectionStatus`, which keeps the lifecycle free of a transport dependency and avoids a module dependency the frontend does not otherwise need. 8 tests
- [X] T079 [US1] **PARTIAL, and the remainder is stated.** `DocumentReplay` applies a resumed session's edits in order, continues past a rejection rather than losing the edits behind it, and reports what it could not apply. 6 tests. **What is not done**: the `SharedDocuments` adapter over `platform/kernel/pasta`. `DocumentEntity.mutate` takes a `ChangeScope`, which only a live kernel transaction provides, so the adapter cannot be unit tested and is not written on speculation. The platform already binds an editor to a shared document per client through `EditorEntity`, which carries a `ClientId`, so a controller session should reuse that rather than introduce a parallel binding. Sequence the adapter with the end-to-end suite
- [X] T080 [US1] `PendingEditLog` buffers the edits a disconnected client makes. It is a log rather than a set, because replaying two edits to one file in the wrong order produces different content. `drain` empties it, so a replayed edit is never replayed twice, which a test asserts
- [X] T081 [US1] `SessionRetention.resume` returns the same `SessionId` it was given. Contract section 4 states that a reconnection resumes a session rather than starting one
- [X] T082 [US1] `SessionRetention.expire` returns an `ExpiredSession` carrying the edits that could not be replayed, so a caller can put them where the user recovers them. FR-015 forbids discarding the work silently, and a test asserts the lost edits come back
- [X] T083 [US1] `./tests.cmd --module intellij.platform.remoteDev.protocol.tests` reports **48 tests, 48 passed**. The suite totals 117 across four modules. The 100-interruption test found a real defect first: `RetentionWindow.DEFAULT` constructed a value whose `init` read `DEFAULT`, recursing until the stack ran out

### P1.6 Port forwarding (FR-016)

- [X] T084 [P] [US1] `PortForwardingTest.kt`, written first. 8 tests covering consent, reuse, per-session isolation and the leak rule
- [X] T085 [US1] `PortForwarder.forward` with `PortOrigin.USER`. A port the user asked for needs no further consent, because asking is the consent and prompting again would be noise. `Tunnels` is the seam over the platform's tunnel support, so the rules can be tested without a host
- [X] T086 [US1] `PortOrigin.DETECTED` covers a port that a launched process opened. **Detecting the port itself is host work** and belongs with the execution environment adapter; what is implemented and tested here is how a detected port is treated once seen
- [X] T087 [US1] `ForwardConsent` gates a detected port. A test asserts that no tunnel is opened when consent is refused, not merely that the call returns nothing. The prompt itself is user interface work for the frontend module
- [X] T088 [US1] `PortForwarder.closeSession` closes every tunnel its session opened. A tunnel that outlives its session is a leak, and it leaves a route into the host open after the session that justified it has gone. Two tests: nothing survives a close, and closing one session leaves another's tunnels alone

### P1.7 Close the FR-014 capability gaps

Eight of the ten capabilities that FR-014 names already have a frontend and backend split. Two do not.
See [research.md](./research.md) decision D7.

- [ ] T089 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the refactoring backend module descriptor. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T090 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the refactoring frontend module descriptor. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T091 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the refactoring `@Rpc` interfaces. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T092 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the refactoring backend service. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T093 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the refactoring frontend through `SplitComponentProvider`. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T094 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the find frontend module descriptor. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T095 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the find and replace frontend. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T096 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create registration of both frontends in `product-modules.xml`. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [ ] T097 [US1] **DEFERRED. T098 shows this slice is mis-sized.** Create the Bazel regeneration for those modules. Splitting refactoring is not a module scaffold: the subsystem drives dialogs, previews, conflict resolution and undo across every language, and a split means deciding for each what state crosses the wire and what runs where. Find in Files is the same at a smaller scale. Creating empty modules now would be speculative scaffolding, which constitution Principle 2 forbids under YAGNI. Size this as its own slice, with `split-coverage.md` as the evidence
- [X] T098 [US1] Survey written to `specs/001-ultimate-feature-parity/split-coverage.md`. **18 modules are paired, 14 have a backend with no frontend, and refactoring has neither.** Nine of the 14 are legitimately backend-only, such as indexing, the kernel and the transport, where a frontend would be meaningless. Five are real gaps a user would notice: find and replace, the TODO view, the script debugger, update notifications, and the internal actions. Only find and replace is named by FR-014, so the other four do not block P1, but they are now sized rather than discovered later
- [ ] T099 [US1] Run the FR-014 capability matrix end to end against a remote session with the IDE Starter and the UI Driver, per `quickstart.md` Check 6

**Note on T098**: [plan.md](./plan.md) flags this. Closing the two FR-014 gaps does not make every
dialog render. The survey turns an implementation surprise into a known list.

### P1.8 Security, credentials, and provenance (FR-001, FR-004, FR-007, FR-008, FR-018)

- [X] T100 [P] [US1] `SecretRedactionTest` covers both lines of defence. The first is the types: `SessionToken` hides its own value, so a log statement written by somebody who never thought about redaction still cannot leak it, and a test asserts the value survives for the one caller that presents it. The second is `LogRedaction`, for a raw value that reached a string some other way, such as a message a peer sent
- [X] T101 [US1] `SessionTransportSecurity` allows exactly two shapes: TLS on the socket, or plain text on a loopback address, where the bytes never reach a network interface and an SSH tunnel carries them off the machine. Refusing the second would refuse D2's own design and buy nothing. Loopback is decided from the text of the address and never by resolving a name, because a name resolves wherever its owner points it, so `localhost.attacker.example` would otherwise turn the check off. Adding `INSECURE_TRANSPORT` broke an exhaustive `when` in `SessionFailureReporter` and the pinned wire-name set in `ContractEvolutionTest`. Both were right to fail: the first would have rendered a literal `{0}` to a user under an `else` branch, and the second asks whether a new code travels, since an older peer cannot deserialize a name it does not know. The answer is recorded in contract section 6.1: this code is decided locally, before the handshake, so it never travels
- [X] T102 [US1] `CredentialStorage` takes the platform's own `CredentialStore` interface and defines no seam of its own, because a wrapper over a platform interface adds a name and no behaviour, and a test drives the platform interface just as easily. `HostRecord` takes a `CredentialRef`, so the type system, not discipline, keeps a secret out of it. Tests assert the secret is absent from the reference and from the key the store is indexed by, that revocation deletes rather than marks, and that storing twice for one host replaces rather than orphans the old credential
- [X] T103 [US1] `SessionTokenRegistry` issues, validates and revokes a session credential, and is now wired into `HostSessionPolicy`, **replacing the fail-closed stub from T034**. Three properties, each with a test: the token comes from `SecureRandom`, so it cannot be guessed; the registry holds a SHA-256 digest and never the token, so a leaked registry yields nothing a client could present, per FR-008; and revocation takes effect at once rather than waiting for an expiry, because control that waits is not control, per FR-018. Comparison uses `MessageDigest.isEqual`, so a mismatch does not leak where two values first differ. The clock is monotonic, because wall time would let a clock change extend a token's life
- [X] T104 [US1] `LogRedaction` masks a known credential in text bound for a log. Two rules keep it from making logs useless: a value under eight characters is not masked, because masking a two character value would redact ordinary words, and a secret is matched as text rather than compiled as a pattern, so a credential containing a dot or a bracket does not become a wildcard. It is the second line, not the first: `SecretScanner` treats the redaction marker as a non-finding, which closes the loop between the emitting side and the scanning side
- [X] T105 [US1] `AdminHostPolicy` reads a host allowed list and a TLS requirement through the platform's `OsRegistryConfigProvider`, which already handles the Windows registry, `/etc/xdg`, and Application Support. Absence and emptiness deliberately differ: no setting means no rule, because a machine with no administrator must keep working, while a setting that names no host means no host, which is both the literal and the safe reading. The lookup is a constructor parameter so a test drives the rules without a machine to configure. Two things are recorded rather than hidden: `OsRegistryConfigProvider.get` writes the value it read into the log, so nothing secret may ever use these keys, and its paths carry the JetBrains name, which sits badly with FR-002. The tension is written up under Known tensions in `docs/fork-platform-changes.md`, because the alternatives are a fifth upstream file change or duplicating the class for one path segment
- [X] T106 [US1] `SecretScanner` plus the `//build/thin-client:verify-artifacts` target. **Run against the real distribution: nothing found across 133 components' worth of artifacts.** The patterns are few and high signal on purpose, because a scanner that cries wolf gets switched off and finds nothing at all. A redaction marker this fork prints, such as `SessionToken(redacted)`, is explicitly not a finding, since flagging it would punish the behaviour FR-008 asks for
- [X] T107 [US1] `LicenseValidator` plus the same target. **The first real run found 5 licences my allowlist had missed**: Creative Commons 2.5 Attribution, JDOM License, zlib/libpng, MPL 2.0 and codehaus. My first pass took the common licences and missed the tail. Each is permissive or file-level copyleft, so each was added with that reasoning recorded. The list is an allowlist, not a deny list: a deny list passes anything nobody thought to forbid, and the failure mode is shipping a component this fork has no right to distribute. It now reports `Checked 133 component(s). Every licence is on the allowed list.`
- [X] T108 [US1] Writing this test found that **nothing in production called `BackendTrustGate`**. The rule existed and was unit-tested, but had no enforcement point, so SC-015 was not actually met. `BackendOperationDispatcher` is that point: the action is a lambda that is not called when the gate refuses, so a future call site cannot reach the operation without passing the check. The test uses a real executable script that leaves a file behind, and it carries a control that runs the same script through the same dispatcher with trust granted. Without the control the untrusted test would pass for a script that could never run, which is the failure this session already met once. Verified by mutation: with the gate forced to allow, the untrusted test reported `Ran(value=0)` and the script really executed
- [X] T109 [US1] The provenance statement now says what the automated checks prove and what they cannot. They prove the distribution ships no incompatible licence and no credential; they cannot prove authorship. The claim rests instead on how the work was done, and it cites the two rejected shortcuts as evidence, since a rejected shortcut carries more weight than a promise: `libraries/rd` (D4) and the closed-source Lux host (D5)

**Checkpoint**: User Story 1 is fully functional. Every check in `quickstart.md` passes.

---

## Phase 4: Polish and Cross-Cutting Concerns

- [X] T110 [P] `platform/remoteDev-protocol/README.md`, not the `docs/README.md` this task named: the machine's global `~/.gitignore` carries a bare `docs/` rule, so the file was silently unstageable and would have been lost. A module README is the more usual place for it anyway. It walks a session from framing through transport, handshake, version negotiation, credentials, failure and end, and names the contract as the document that wins on disagreement. It closes on how to change the contract, because `ContractEvolutionTest` failing on an *added* enum value reads as noise until you know that a `@Serializable` name a peer does not hold cannot be deserialized. `INSECURE_TRANSPORT` is left in as the worked example of a value that never travels
- [X] T111 [P] Count stays 4. Eight tracked files differ in total; the other four are registry or generator output that nobody chooses the contents of, and the ledger now names all four, including the two generated `main/BUILD.bazel` files it had omitted. `WellKnownCommand.java` was the one applied change with no rebase note, and it now carries one drawn from the T112 measurements
- [X] T112 The instrument mattered more than the run. `git merge-tree HEAD idea/2026.3-eap-1` reports 195 conflicting paths, and quoting that as the fork's rebase cost would have been wrong: nearly all of it is upstream against upstream in files this fork never touched. Merging each changed file on its own, three ways, gives the real answer: 8 of 8 clean against the three most recent tags. The finding is an inversion. `.idea/modules.xml` (654 upstream commits in twelve months) and `bazel-generated-file-list.txt` (556) merged clean everywhere, while the two `main/BUILD.bazel` files (12 and 10) conflicted at both older tags. Long append-only registries absorb an edit; a short file with one `deps` list does not. Rebase pain follows where an edit lands, not how often the file changes
- [X] T113 [P] `VerifyPluginTargets` lives in `build/thin-client/src/`, where the fork's other build code already is; the path in this task predates that module. It checks two things, and the second is the reason it exists. A declared range that excludes either build is visible. A dependency on a fork-only module is not: such a plugin installs on stock Community Edition, declares a range that admits it, and fails at load with a missing module. The range check would never see it. `BuildNumber` does the parsing and comparison, wildcards included, so only the rule is written here. The fork-only module list is written out rather than derived, because a check that computes its expectations from the thing it checks cannot fail. No distribution scanner yet: there is no plugin slice to scan until P2, and writing descriptor parsing against zero descriptors is how you get a parser nobody has tested
- [X] T114 [P] `NEWER_WITH_SAME_BASELINE`, chosen by the user from four options. The baseline is the honest boundary: within one the platform API is stable enough that a slice built against any member should work against a later one, and across one a wider range would only move the failure from load time to a call site. `EXACT` and `RESTRICTED_TO_SAME_RELEASE` were rejected because both would need a separate artifact per slice for stock Community Edition and for the fork, which is the thing FR-056 asks not to need. Presenting the option previews surfaced the part that matters more than the choice: `PluginXmlPatcher` derives `since-build` from `context.buildNumber`, so a slice built here declares this fork's number and stock Community Edition, being older, refuses it. The setting is necessary and not sufficient, and the residual gap is recorded in the code next to it
- [X] T115 Recorded in a table at the end of `quickstart.md`. Check 0 passes outright. Checks 2, 4 and 5 are covered by unit tests but not in the end-to-end form the document describes, and they say so. Checks 1, 3, 6 and 7 need a running session or a remote host and name the task that will run them. Nothing has been run and failed. The run also found a defect the Verification Standards name: Check 8 invoked `//build:scan-artifacts-for-secrets` and `//build:validate-licenses`, neither of which was ever created under those names. Replaced with the target that does both jobs, since both walk the same distribution
- [ ] T116 Confirm the SC-002 latency target against the T038 baseline in `specs/001-ultimate-feature-parity/latency-baseline.md` and record any regression there
- [X] T117 [P] The audit found one real violation: `AgentSelection.mismatch` builds text that FR-010 sends to a user. It now resolves `agent.platform.mismatch` from a new `RemoteDevProvisioningBundle`, which the module needed because the alternative was adding keys to upstream's `RemoteDevUtilBundle` and a fifth upstream file. The rest are exempt and now say so in the code: `SplitBackendStarter` writes operator diagnostics to a host terminal, where the platform does not localise `ApplicationStarter` output either, and translating them would put an operator's diagnostics in the end user's language; `FirstRunBudget.summarise` is measurement text. Both are marked `@NonNls` so the exemption is visible rather than assumed
- [X] T118 Both pass. `format.check` covers Starlark only, so it is not evidence about the Kotlin
- [x] T119 Fill `.specify/memory/constitution.md` with `/speckit-constitution`. DONE at v1.0.0. The plan's Constitution Check now records a real nine-principle evaluation
- [X] T120 One real finding. `BackendSessionApi.isOpenHere` read `openProjects` and then `basePath` with no read action and no disposal check, reachable from the `handshake` RPC handler; `getOpenProjects` documents that a caller outside a read action must check `isDisposed` on each project, because the array is a snapshot. Fixed with the disposal check rather than a read action: this runs on the thread answering a handshake, and taking the read lock there would block that answer behind any write in progress. Provisioning touches no PSI, VFS or project model at all, so it has no contract to break. `closeProjects` was already correct. `reportTrustState` is recorded rather than closed, because the absence of a threading annotation upstream is weaker evidence than its presence. Nothing performs a long read, so nothing needs cancellation yet
- [X] T121 [P] This was not optional after all: the platform's own `ApiDumpFilePresenceTest` was failing on all four remoteDev modules, a regression the fork introduced and had not caught. Generated with the platform's generator rather than by hand, which took finding that `TestingTasksImpl` only forwards a system property to the forked test JVM when it is prefixed `pass.`, so the invocation is `-Dpass.api.dump.test.update.files=true`. The fifth module turned out to be `intellij.platform.ijent.agent`, not the build module, which is not a platform module. The dumps say the surface is right: backend and frontend expose nothing at all, and protocol and provisioning expose only their message bundles, which are public because `DynamicBundle` requires it
- [X] T122 Resolve the Principle 1 violation before T032 runs. Determine whether an extension point already exists for registering a `ClientType.CONTROLLER` session in `platform/platform-impl/src/com/intellij/openapi/client/ClientSessionManagerImpl.kt`. Use the EP if one exists. If none exists, record the justification in `docs/fork-platform-changes.md`
- [X] T123 [P] `CleanCodeGate` enforces the three rules decidable from source text, and documents why it refuses the other three. Complexity needs a parser: counting keywords cannot tell an exhaustive `when` over a sealed type from an ordinary one, and Principle 2 exempts the first, so detekt's own rule would enforce something different from what is written down. Magic numbers and dead code are worse: every text-level version is mostly false positives, and a gate that reports nothing is read as a gate that found nothing. The gate found a bug in itself, which is the argument for dogfooding: a `'"'` character literal put the stripper into string mode and `'{'` counted as structure, so it reported its own 20-line function as 76. Fixed, regression-tested, and the repository-wide run now reports 0 outliers across all five fork modules, after flattening two functions the gate was right about
- [X] T124 [P] Already satisfied, verified rather than changed. `BackendSessionRegistry` and `SessionTokenRegistry` are light services carrying `@Service(Service.Level.APP)`, which is the modern form and needs no XML. No static mutable state anywhere in the four modules: every `object` is a stateless holder of functions, and all mutable state lives in instances
- [X] T125 [P] Two of the three minimums are met, and the third is met only partly. All tests are headless and there is no mocking framework on any test classpath, so the no-mocking rule cannot be broken quietly rather than merely being unbroken. The fixture minimum is where the honest gap is: every test is a unit test, which is the right fixture for domain logic and the reason the suite runs in under a second, but no surface that needs a running IDE has one. The map names `UntrustedProjectTest` as where that gap shows inside a passing test: it proves the gate refuses, not that every path to running project code goes through the gate
---

## Dependencies and Execution Order

### Phase dependencies

- **Setup (Phase 1)**: No dependency. Start immediately
- **Foundational (Phase 2)**: Depends on Phase 1. BLOCKS every User Story 1 task
- **User Story 1 (Phase 3)**: Depends on Phase 2 completing at T026
- **Polish (Phase 4)**: Depends on Phase 3

### Dependencies inside User Story 1

The sub-phases are not fully sequential. The real graph is below.

```text
P1.1 (backend)  ─┬─> P1.3 (protocol) ──> P1.5 (reconnection) ──> P1.8 (security)
P1.2 (client)   ─┘                   │
                                     └─> P1.4 (provisioning) ──> P1.6 (ports)

P1.7 (capability gaps) ── independent of P1.3 to P1.6. Needs only P1.1 and P1.2
```

- P1.1 and P1.2 run in parallel. Together they produce the local split, which is the MVP-0 checkpoint
- P1.3 needs both, because negotiation needs two peers
- P1.4 needs P1.3, because a provisioned backend must complete a handshake
- P1.5 needs P1.3 for the session identity
- P1.6 needs P1.4 for the host tunnel
- P1.7 needs only P1.1 and P1.2, so it can proceed alongside P1.3 to P1.6
- P1.8 touches every earlier sub-phase, so it goes last

### Parallel opportunities

- T002 to T007 create nine separate descriptors and all run in parallel
- T013, T014, T015 are three separate files with no dependency between them
- T023 and T024 are separate test files
- T069, T070, T071 add three host kinds in three separate places
- T089, T090, T094 create three separate module descriptors
- P1.7 as a whole runs alongside P1.3 to P1.6 with a second developer

---

## Parallel Example: Phase 2 Foundational

```bash
# Three wire type files, no dependency between them:
Task: "Define serializable session types in platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/SessionModels.kt"
Task: "Define ProtocolVersion in platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/ProtocolVersion.kt"
Task: "Define the failure code enum in platform/remoteDev-protocol/src/com/intellij/remoteDev/protocol/SessionFailure.kt"

# Two failing test files, no dependency between them:
Task: "Write HandshakeTest.kt in platform/remoteDev-protocol/testSrc/com/intellij/remoteDev/protocol/"
Task: "Write VersionNegotiationTest.kt in platform/remoteDev-protocol/testSrc/com/intellij/remoteDev/protocol/"
```

---

## Implementation Strategy

### MVP-0: the local split (fastest demonstrable value)

1. Complete Phase 1 (T001 to T012)
2. Complete Phase 2 (T013 to T026)
3. Complete P1.1 (T027 to T038) and P1.2 (T039 to T050)
4. **STOP and VALIDATE**: run `bazel run //build/launch:ide-split-mode`. A backend and a client start
   as two local processes and the client shows the project
5. This needs no remote machine and it proves the architecture

### MVP-1: the full user story

6. Add P1.3 (protocol), then P1.4 (provisioning). At this point a real remote host works
7. Add P1.5 (reconnection). SC-003 becomes measurable
8. Add P1.6 (ports) and P1.7 (capability gaps)
9. Add P1.8 (security). Do not ship before this
10. Run every `quickstart.md` check

### Parallel team strategy

- Developer A: P1.1, then P1.3, then P1.5
- Developer B: P1.2, then P1.4, then P1.6
- Developer C: P1.7 from the MVP-0 checkpoint onward, then the T098 survey
- All three converge on P1.8

### Order rules that matter

- T038 measures latency before P1.3 fixes the message shape. Do not reorder it
- T098 surveys split coverage before the work is declared done. It converts a surprise into a list
- T030 and T032 are the only two upstream platform changes. Every addition to that count needs a
  reason recorded in `docs/fork-platform-changes.md`, because FR-054 depends on keeping it small

---

## Notes

- `[P]` means a different file and no dependency on an incomplete task
- Every task in Phase 3 carries `[US1]`, because this list covers one user story
- Commit after each task or each logical group
- A test task writes a FAILING test first. Confirm the failure reason before implementing
- Run `./build/jpsModelToBazelCommunityOnly.cmd` after any `.iml` change, and never edit a generated
  `BUILD.bazel` by hand
