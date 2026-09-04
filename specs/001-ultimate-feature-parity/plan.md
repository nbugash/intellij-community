# Implementation Plan: Remote Development (P1)

**Branch**: `feature/convert-to-ultimate-ed` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-ultimate-feature-parity/spec.md`, User Story 1 only.

**Scope**: This plan covers User Story 1 (P1) and the requirements FR-001 to FR-019, FR-052, FR-053,
FR-054, and FR-057. It does not cover User Story 2 to User Story 10. The spec requires one plan for
each slice.

---

## Summary

A developer opens a project that lives on a remote machine. A small local application shows the
editor and the tool windows. The IDE itself runs on the remote machine.

The survey in [research.md](./research.md) changed the shape of this work. The Community Edition
already contains the client and server split. It has an RPC transport, a per-client session model, a
shared document model, 39 frontend modules, 68 backend modules, and a host access layer that covers
WSL, Docker, and SSH. What is absent is the layer that joins them: a backend entry point, a
controller session registration, a connection handshake, and a thin client product.

The technical approach is therefore to **complete an existing architecture, not to invent one**. We
run the IDE in `ProductMode.BACKEND` behind a new `ApplicationStarter`. We build a second product in
`ProductMode.FRONTEND` as the thin client. We join them with `fleet/rpc` through
`platform/kernel/rpc`. We provision the backend with EEL and IJent. We write our own agent binary and
our own session protocol, because neither exists here and FR-052 forbids us to reuse a proprietary one.

---

## Technical Context

**Language/Version**: Kotlin for new code, with Java where an existing platform class requires it.
The bundled runtime is JetBrains Runtime 25, pinned at `build/dependencies/dependencies.properties`
as `runtimeBuild=25.0.4.1b583.48`.

**Primary Dependencies**: `fleet/rpc` and `fleet/rpc.server` for transport. `platform/kernel/rpc`,
`rpc.backend`, and `rpc.lite` for service resolution. `platform/remote-topics` for server push.
`platform/kernel/pasta` and `fleet/andel` for shared documents. `fleet/rhizomedb` for entity state.
`platform/eel` and `platform/ijent` for host access. `kotlinx.serialization` for the wire format.
All are already in the repository and all are Apache 2.0.

**Storage**: The host holds the project sources, the indexes, and the IDE caches. The thin client
holds no project source. The client stores only its own settings, its recent host list, and its
credentials. Credentials go to the platform credential store, never to a file.

**Testing**: `./tests.cmd --module <module> --test <FQN>` for unit and integration tests, as
`AGENTS.md` requires. End-to-end tests use the IDE Starter and the UI Driver, per the repository's
`driver-ui-tests` skill. The development loop uses
`build/launch/src/com/intellij/tools/launch/ide/splitMode/`, which already starts a backend and a
frontend as two processes and can put the backend in Docker.

**Target Platform**: The thin client ships for Windows, macOS, and Linux, on x64 and aarch64. That is
the six-way matrix in `BuildTasksImpl.kt` `SUPPORTED_DISTRIBUTIONS`. The backend runs on a host that
EEL can reach: a machine over SSH, a Linux environment on Windows, or a container.

**Project Type**: A desktop application plus a backend service. The two ship as two products from one
repository.

**Performance Goals**: 95% of keystrokes appear in under 50 ms on a link with 100 ms round-trip time.
95% of completion popups appear in under 300 ms. A 50,000-file project reaches a working editor in
under 10 minutes on first use. These come from SC-001 and SC-002.

**Constraints**: The fork must stay rebasable on upstream Community Edition, so each platform change
must be small, isolated, and documented (FR-054). A session must survive an outage of up to five
minutes with no lost edit (FR-015). The client and the backend must negotiate a protocol version and
the backend must support the two most recent versions (FR-057). No credential may reach a log or a
version-controlled file (FR-008).

**Scale/Scope**: One backend serves one project to one or more clients. One host runs several
backends at once (FR-019). The target project size for acceptance is 50,000 files.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Evaluated against constitution v2.0.0. Result: one pass, two conditional, one gap, one violation
of the new Principle 3.**

Version 2.0.0 cut the set from nine principles to five and renumbered it, so the previous evaluation
in this plan cited numbers that no longer mean the same thing. This replaces it.

| Principle | Result | Evidence |
|---|---|---|
| 1. Clean Architecture | PASS | The graph is acyclic and points inward. `remoteDev-protocol` declares the contracts and depends on neither product. `remoteDev-backend` and `remoteDev-frontend` both depend on it and never on each other. The generated Bazel targets confirm the edges. Extension points carry the inversion: `appStarter` at T029 and `remoteApiProvider` at T034. Verified after implementation, not only on paper |
| 2. Clean Code | **CONDITIONAL** on T123 | The only committed code is `platform/remoteDev-protocol/src/RemoteDevProtocolBundle.kt`. Measured: 17 lines, longest function 3 lines, complexity 1, nesting 1, no magic number, no dead code. It passes the tightened limits. No lint gate enforces them yet, so the result is conditional rather than a pass |
| 3. Resource Ceiling | **VIOLATION, already committed** | See the finding below. T001 as written also conflicts with this principle |
| 4. Threading | **GAP** | Neither the plan nor the task list mentions the read-write lock, a read action, a write action, or a dispatcher. T120 closes it. This is unchanged from the previous evaluation and remains the most serious open item |
| 5. Testing | **CONDITIONAL** on T125 | Test-first is followed and every measurable criterion has a task that produces its number: T038 for SC-002, T073 for SC-001, T076 for SC-003, T053 for SC-018. The fixture rule is satisfied by construction, because the protocol work has no PSI or editor surface and correctly uses plain JUnit. The per-feature minimum is not yet checked |

**Finding against Principle 3, Resource Ceiling.** The principle is new, and the implementation run
that produced the current module scaffolding did not follow it. The Bazel build of the five new
modules ran without measuring free memory first and without passing `--local_ram_resources`. It
happened to succeed on a host with 61 GiB total and 54 GiB available, so no harm resulted, but the
practice was wrong under the rule now in force. Two corrections follow.

1. T001 instructs a baseline build of `//platform/...`. That is exactly the broad target pattern the
   principle names as the most likely way to breach the ceiling. T001 is reworded.
2. Every future Bazel invocation in this feature measures first and passes an explicit cap.

**Note on the order of events.** This constitution was written after this plan, which creates a
standing risk that principles get shaped to bless the design. The findings above are the evidence
against that. The newest principle catches a mistake already made during implementation, one gap
remains open, and two more principles are only conditionally satisfied.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-ultimate-feature-parity/
├── spec.md              # Programme specification, all ten slices
├── plan.md              # This file, P1 only
├── research.md          # Phase 0 decisions D1 to D9
├── data-model.md        # Phase 1 entities
├── quickstart.md        # Phase 1 validation guide
├── contracts/
│   └── session-protocol.md   # The wire contract, FR-052 source of truth
├── checklists/
│   └── requirements.md
└── tasks.md             # Created by /speckit-tasks, not by this command
```

### Source Code (repository root)

The plan adds a small number of modules and changes a small number of existing files. The split
between the two reflects FR-054, which requires each platform change to be minimal and documented.

```text
platform/
├── remoteDev-backend/                 # NEW. The host side
│   ├── src/.../RemoteDevHostStarter.kt        # ApplicationStarter for the reserved command
│   ├── src/.../ControllerSessionRegistrar.kt  # Registers a ClientType.CONTROLLER session
│   └── src/.../BackendSessionService.kt
├── remoteDev-protocol/                # NEW. Shared by both products
│   ├── src/.../SessionApi.kt                  # @Rpc interfaces
│   ├── src/.../ProtocolHandshake.kt           # Version negotiation, FR-057
│   └── src/.../SessionModels.kt               # kotlinx.serialization types
├── remoteDev-frontend/                # NEW. The thin client side
│   ├── src/.../FrontendSessionController.kt
│   └── src/.../HostConnectionManager.kt       # Implements the existing ConnectionManager
├── remoteDev-provisioning/            # NEW. Backend deployment over EEL
│   ├── src/.../BackendProvisioner.kt          # EelArchiveApi upload, EelExecApi start
│   ├── src/.../PortForwarder.kt               # EelTunnelsApi, FR-016
│   └── src/.../HostRegistry.kt
├── ijent-agent/                       # NEW. Our own agent binary, FR-001 and D4
│   └── src/...
├── main/
│   ├── intellij.platform.frontend.main/       # EXISTING aggregator, 16 deps. Thin client root
│   └── intellij.platform.backend.main/        # EXISTING aggregator, 25 deps. Backend root
├── core-api/src/com/intellij/idea/
│   └── WellKnownCommand.java                  # CHANGED. Names already reserved, starter wired
├── platform-impl/src/com/intellij/openapi/client/
│   └── ClientSessionManagerImpl.kt            # UNCHANGED. T122 proved no edit is needed
├── refactoring/                       # NEW split, FR-014 gap
│   ├── frontend/
│   └── backend/
└── find/
    └── frontend/                      # NEW split, FR-014 gap. find.backend already exists

build/
├── src/org/jetbrains/intellij/build/
│   └── ThinClientProperties.kt        # NEW. ProductProperties for the thin client
├── src/ThinClientInstallersBuildTarget.kt     # NEW. Build target main
└── dev-build.json                     # CHANGED. Registers the new product

community-resources/resources/idea/
└── ThinClientApplicationInfo.xml      # NEW. Required by the platformPrefix

thin-client-images/                    # NEW. Icons that the build validates
```

**Structure Decision**: The repository is a monorepo whose modules are JPS `.iml` files that generate
Bazel targets. New work therefore becomes new modules under `platform/`, not a new top-level tree.

I put the protocol in its own module, `platform/remoteDev-protocol`, because both products depend on
it and neither may depend on the other. I kept provisioning separate from the session, because
provisioning uses EEL and runs before a session exists, while the session uses `fleet/rpc` and runs
after. Mixing them would tie the connection lifetime to the host lifetime, which FR-019 forbids,
because one host runs several backends.

I did not create a module for the thin client user interface. The existing frontend modules already
supply it through `SplitComponentProvider`. Creating a parallel user interface would duplicate 39
modules for no gain.

---

## Implementation phasing

Each phase below is independently testable. A phase that fails does not block the phase before it.

| Phase | Delivers | Proves |
|---|---|---|
| P1.1 | Backend starter and controller session registration | A backend starts and accepts one local client |
| P1.2 | Thin client product and installers | The client builds and starts on three operating systems |
| P1.3 | Session protocol and version negotiation | FR-057. A mismatched pair refuses to connect and says why |
| P1.4 | Provisioning over EEL and IJent, with our agent | FR-012 and FR-013. A backend starts on SSH, WSL, and a container |
| P1.5 | Reconnection and unsaved state | FR-015. 100 induced outages lose no edit |
| P1.6 | Port forwarding | FR-016 |
| P1.7 | The refactoring split and the find frontend | Closes the two FR-014 gaps |
| P1.8 | Transport security and credential revocation | FR-018 and FR-008 |

P1.1 and P1.2 together give a working local split. That is the earliest point at which the work can
be demonstrated, and it needs no remote machine.

---

## Complexity Tracking

The Constitution Check produced no violations, because no constitution exists to violate. The table
below instead records the three places where this design adds real complexity, and why a simpler
option does not work.

| Addition | Why needed | Simpler alternative rejected because |
|---|---|---|
| A separate `remoteDev-protocol` module | Both products need the wire types, and neither may depend on the other | Putting the types in the backend forces the client to depend on the backend, which defeats a thin client |
| Our own IJent agent binary | `IjentExecFileProvider` throws `IjentMissingBinary`, and the binary ships in a proprietary plugin | Reusing the proprietary agent breaks FR-001 and FR-003 |
| A second product with its own installers | A thin client must stay small, and it needs its own icon, launcher, and bundled content | One product that switches mode at run time ships the whole IDE to every client |

---

## Risks

**The split coverage decides the felt quality.** Eight of the ten FR-014 capabilities have a split
today. Any dialog or tool window with no split renders nothing in the thin client. The count of 39
frontend modules against 68 backend modules shows the gap is real and wider than FR-014 alone.
P1.7 closes the two capabilities that FR-014 names. It does not close every dialog in the product.
`/speckit-tasks` must budget for a survey of the tool windows that P1 promises.

**The upstream rebase burden starts on day one.** FR-054 requires the fork to stay rebasable. This
plan now changes exactly one existing platform file, `WellKnownCommand.java`. T122 removed the
second by finding a public registration path. Keeping the count at one is the main long-term cost
control, and it should be a review rule rather than an intention. The ledger at
`docs/fork-platform-changes.md` makes the count visible.

**`ProtocolVersion` is a slot, not a mechanism.** `fleet/rpc` gives a version type whose current
value is the string `"1"`. FR-057 needs a real handshake with a supported range. Treat P1.3 as new
work, not as configuration.

**The performance target is unproven on this transport.** SC-002 asks for 50 ms keystroke latency at
95% on a 100 ms link. No measurement exists for `fleet/rpc` under that load. P1.1 should produce a
latency measurement before P1.3 fixes the protocol shape, because a protocol change is cheap early
and expensive late.
