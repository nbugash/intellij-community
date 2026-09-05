# Quickstart: Validating Remote Development (P1)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)
**Date**: 2026-09-04

This guide gives the runnable checks that prove P1 works. Each section maps to a phase in
[plan.md](./plan.md) and to the requirements that the phase delivers. Run the checks in order,
because a later one assumes that an earlier one passes.

For the entity rules see [data-model.md](./data-model.md). For the wire behaviour see
[contracts/session-protocol.md](./contracts/session-protocol.md). This guide does not repeat them.

---

## Prerequisites

| Need | Detail |
|---|---|
| Build system | Bazel. `AGENTS.md` states that Bazel produces the shipping artifacts |
| Repository state | `.iml` files are the source of truth. After you add a module, run `./build/jpsModelToBazelCommunityOnly.cmd` |
| A remote host | A machine reachable over SSH, plus a Linux environment on Windows, plus a container runtime. FR-013 needs all three |
| A test project | A repository of at least 50,000 files, for SC-001 and SC-005 |

### Which commands exist today

I verified every command in this guide against the repository. The distinction below matters, because
a command that does not exist yet is a task, not a check.

**Exists now**: `./build/jpsModelToBazelCommunityOnly.cmd`,
`./build/assertJpsModelToBazelCommunityPaths.sh`, `bazel run //:format.check`, `./tests.cmd`,
`installers.cmd`, and the Bazel target `//build/launch:launch`.

**P1 must create**: every target marked `[P1 CREATES]` below. The harness sources for the split-mode
launcher already exist at `build/launch/src/com/intellij/tools/launch/ide/splitMode/`, in the module
`intellij.idea.tools.launch`. P1 must give them a runnable target. The two verification targets in
Check 8 do not exist in any form and P1 must write them.

---

## Check 0: the repository model stays consistent

Run this after any module change, before anything else. A drift between the JPS model and Bazel
breaks every later check in a way that is hard to read.

```
./build/jpsModelToBazelCommunityOnly.cmd
./build/assertJpsModelToBazelCommunityPaths.sh
bazel run //:format.check
```

**Expected**: no diff reported. If `format.check` reports one, run `bazel run //:format`, inspect the
change, and run the check again.

---

## Check 1: the local split runs (phase P1.1 and P1.2)

This check needs no remote machine. It is the earliest demonstration of the work.

The repository already has a harness that starts a backend and a frontend as two processes. It lives
at `build/launch/src/com/intellij/tools/launch/ide/splitMode/`. Use it rather than building
installers, because the loop is far shorter.

```
# [P1 CREATES] A runnable target over the existing splitMode harness sources.
# Sources: build/launch/src/com/intellij/tools/launch/ide/splitMode/
# Module:  intellij.idea.tools.launch   Existing target: //build/launch:launch
bazel run //build/launch:ide-split-mode -- --project <path to the test project>
```

**Expected**:

1. Two processes start. One runs in `ProductMode.BACKEND`. One runs in `ProductMode.FRONTEND`.
2. The client shows the project tree of the test project.
3. The client process holds no copy of the project sources.

**Proves**: FR-011 in its local form, and the controller session registration that
`ClientSessionManagerImpl` lacks today.

**Run the unit tests for the new modules.** `AGENTS.md` requires this after a code change. Name the
module and give a fully qualified test name. A simple class name matches nothing.

```
./tests.cmd --module intellij.platform.remoteDev.backend --test 'com.intellij.remoteDev.backend.*'
./tests.cmd --module intellij.platform.remoteDev.protocol --test 'com.intellij.remoteDev.protocol.*'
```

---

## Check 2: version negotiation refuses a mismatch (phase P1.3)

This check proves FR-057 and SC-018. It needs two builds whose supported version ranges differ.

```
./tests.cmd --module intellij.platform.remoteDev.protocol --test 'com.intellij.remoteDev.protocol.HandshakeTest'
```

**Expected**:

1. A client and a backend one version apart connect successfully.
2. A client and a backend outside the supported range are refused.
3. The refusal carries `VERSION_MISMATCH`, the client version, and the backend supported range.
4. The refusal happens **before** the backend opens the project. Assert that no project was opened.

Point 4 is the one that a naive test misses. A refusal that happens after the project opens still
fails the contract.

---

## Check 3: a backend provisions on each host kind (phase P1.4)

This check proves FR-012 and FR-013, and it measures SC-001.

Run it once for each of the three host kinds: a machine over SSH, a Linux environment on Windows, and
a container.

```
# [P1 CREATES] Provision and connect, timing the path from address to working editor
bazel run //build/launch:ide-remote-connect -- --host <host> --project <path> --measure
```

**Expected**:

1. The agent uploads and starts with no manual installation step on the host.
2. A working editor opens.
3. The elapsed time from the address to the editor is under 10 minutes on first use, for a project of
   50,000 files. That is SC-001.
4. A second run of the same version does no upload work, because deployment is idempotent.

**Also assert the failure path.** Point the check at a host whose platform differs from the agent
that is offered. The deployment must fail cleanly, leave no partial file, and name a next action.
FR-009 and FR-010 require this.

---

## Check 4: a session survives an outage (phase P1.5)

This check proves FR-015 and SC-003. It is the check most likely to find a real defect, so give it
the induced-failure count that the criterion asks for.

```
./tests.cmd --module intellij.platform.remoteDev.protocol --test 'com.intellij.remoteDev.protocol.ReconnectionTest'
```

**Expected**, across at least 100 induced interruptions:

1. Every outage up to five minutes reconnects with no user action.
2. No unsaved edit is lost in any trial. SC-003 asks for 100%, not for a high percentage.
3. The session keeps its `sessionId` across the reconnection.
4. An outage past the retention window fails with `SESSION_EXPIRED` and reports what was lost. It does
   not discard the work silently.

---

## Check 5: port forwarding (phase P1.6)

This check proves FR-016.

```
# [P1 CREATES] See Check 3
bazel run //build/launch:ide-remote-connect -- --host <host> --project <path>
# In the session, start a web application that listens on a host port
```

**Expected**:

1. A user-requested port is forwarded and the local browser reaches the application.
2. A port that a launched process opens is detected and offered to the user.
3. A detected port is not forwarded until the user consents.
4. Every tunnel closes when the session ends. A leaked tunnel is a defect.

---

## Check 6: the FR-014 capability matrix (phase P1.7)

FR-014 names ten capabilities that must work in a remote session. Eight have a split today. Two do
not. Run the end-to-end suite against a remote session and confirm every row.

Use the IDE Starter and the UI Driver, as the repository's `driver-ui-tests` skill describes.

| Capability | Expected in a remote session |
|---|---|
| Editing | A keystroke appears with no perceptible delay |
| Completion | A popup lists correct candidates |
| Navigation | Go to definition reaches the declaration |
| Search | Search Everywhere returns results from the host |
| Refactoring | A rename updates every reference. **New split in P1.7** |
| Version control | A diff and a commit work against the host repository |
| Run | A run configuration executes on the host and streams its console |
| Debug | A breakpoint is hit, and a variable resolves |
| Test execution | The suite runs on the host and failures navigate to source |
| Terminal | A shell runs on the host |

Find and replace also needs its new frontend. `find.backend` exists alone today.

---

## Check 7: performance (SC-002)

Measure on a link with 100 ms round-trip time. Induce the latency rather than assume it.

**Expected**:

1. 95% of keystrokes appear in under 50 ms.
2. 95% of completion popups appear in under 300 ms.

Run this measurement in phase P1.1, not at the end. A protocol shape is cheap to change early and
expensive to change late. [plan.md](./plan.md) records this as a risk, because no measurement exists
for this transport under this load.

---

## Check 8: security and provenance (phase P1.8)

These checks prove FR-001, FR-004, FR-007, FR-008, FR-018, SC-013, SC-014, and SC-015.

```
# Both checks, over a built distribution: every licence approved, no credential in any shipped file
./bazel.cmd run //build/thin-client:verify-artifacts -- <path to the built distribution>

# Constitution Principle 2, Clean Code. Reports every outlier by name and fails on any
./bazel.cmd run //build/thin-client:clean-code-gate -- platform/remoteDev-protocol/src \
  platform/remoteDev-backend/src platform/remoteDev-frontend/src \
  platform/remoteDev-provisioning/src build/thin-client/src
```

These replace the two targets this document named while they were still planned,
`//build:scan-artifacts-for-secrets` and `//build:validate-licenses`. Neither was ever created under
those names. One target does both jobs, because both walk the same distribution and splitting them
would walk it twice. `LicenseValidator` and `SecretScanner` are the two halves inside it.

The licence check uses an allowed list rather than a denied list, so an unrecognised licence stops
the build and asks a person. Its data comes from
`platform/build-scripts/licenses/src/CommunityLibraryLicenses.kt`.

**Expected**:

1. No credential appears in a log, a diagnostic report, or a version-controlled file.
2. Every session token is revocable, and revocation ends the session at the next operation.
3. The transport is encrypted, and the backend listens on loopback only.
4. Opening an untrusted project executes no build script until the user grants trust.
5. Every distributed component carries a licence that is compatible with this repository.

---

## Exit criteria for P1

P1 is complete when every check above passes and the three statements below hold.

1. A developer with a host address and a project path reaches a working editor in under 10 minutes on
   first use, with no manual installation on the host.
2. The fork rebases onto the current upstream Community Edition release with its platform changes
   applying cleanly or with a documented conflict resolution. FR-054 requires this, and the plan keeps
   the changed platform file count at two to make it achievable.
3. The thin client builds installers for all six supported operating system and architecture pairs.


---

## Record of the run, T115

Run on 2026-09-05, on Linux, against this repository at `feature/personal-customization`.

| Check | Outcome |
| --- | --- |
| 0, repository model | **Pass.** Generator, path assertion, and `format.check` all exit 0 |
| 1, the local split runs | **Not run.** Needs the two-process harness from T022, which is deferred |
| 2, version negotiation | **Covered by unit tests, not by this check.** `VersionNegotiationTest` (7) and `ContractEvolutionTest` (5) assert the refusal. The check as written wants two running processes, which is T099 |
| 3, provisioning per host kind | **Not run.** Needs an SSH host, a WSL host, and a container host |
| 4, session survives an outage | **Covered by unit tests.** `ReconnectionTest` (8) and `SessionReconnectorTest` (8). The end-to-end form is T099 |
| 5, port forwarding | **Covered by unit tests.** `PortForwardingTest` (8). The end-to-end form needs a host |
| 6, FR-014 capability matrix | **Not run.** This is T099, and it needs a running session |
| 7, performance, SC-002 | **Blocked.** Needs the T038 baseline, which needs the T022 harness |
| 8, security and provenance | **Partly run.** The clean-code gate runs and reports 0 outliers across all five fork modules. `verify-artifacts` needs a built distribution; its last run over one reported 133 components, every licence approved and no credential. The five numbered expectations are covered by unit tests: 202 of them, listed in `test-coverage-map.md` |

The shape of this is worth stating plainly rather than leaving to be inferred. Every check that needs
only this repository passes. Every check that needs a running session or a remote host has not been
run, and each names the task that will run it. No check has been run and failed.
