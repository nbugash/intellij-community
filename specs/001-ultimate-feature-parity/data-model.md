# Phase 1 Data Model: Remote Development (P1)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)
**Date**: 2026-09-04

This document defines the entities that P1 introduces, the rules that keep them valid, and the states
that they move through. It covers only User Story 1.

An entity below is new unless the table says that the platform already supplies it. Where the platform
supplies one, the plan reuses it and this document records only the fields that P1 adds.

---

## Entity map

```text
Host  1 ────────< Backend  1 ────────< RemoteSession >──────── 1  ThinClient
  │                  │                      │
  │                  │                      ├──< ForwardedPort
  │                  │                      └──── ControllerSession (platform ClientSession)
  │                  └──── ProjectBinding
  └──< DeployedAgent
```

---

## Host

A machine or environment that can run a backend.

| Field | Type | Rule |
|---|---|---|
| `id` | stable string | Unique. Survives a restart of the client |
| `descriptor` | EEL descriptor | Supplied by the platform. Identifies the environment |
| `kind` | `SSH`, `WSL`, or `CONTAINER` | FR-013 requires all three |
| `displayName` | string | Shown to the user. Never holds a credential |
| `credentialRef` | credential store key | A reference only. FR-008 forbids the value here |
| `platform` | EEL platform | Read from the host. Never assumed from the client |

**Rules.**

- A `Host` never stores a password, a key, or a token. It stores only a key into the platform
  credential store.
- Two descriptors that resolve to one physical machine share an `EelMachine`. Cache a connection by
  the machine, not by the descriptor.
- The `platform` field must be read from the host before any binary is chosen for it. The client
  platform is not evidence of the host platform.

---

## DeployedAgent

Our agent binary, placed on a host so that EEL can reach it.

| Field | Type | Rule |
|---|---|---|
| `hostId` | Host id | Required |
| `version` | version string | Required |
| `targetPlatform` | EEL platform | Must match the host platform exactly |
| `installPath` | host path | Under a directory that the user owns |

**Rules.**

- The deployer verifies the integrity of the binary before it runs it.
- An agent whose version does not match the client requirement is replaced, not reused.
- Deployment is idempotent. A second deployment of the same version does no work.

**States.** `Absent -> Uploading -> Verifying -> Ready -> Superseded`. A failure at any step returns
to `Absent` and leaves no partial file, which FR-009 requires.

---

## Backend

A provisioned IDE runtime on a host. It serves exactly one project.

| Field | Type | Rule |
|---|---|---|
| `id` | stable string | Unique per host |
| `hostId` | Host id | Required |
| `projectPath` | host path | Required. Must exist on the host |
| `productVersion` | build number | Required for the compatibility check |
| `protocolVersions` | list of versions | Must hold at least the two most recent, per FR-057 |
| `listenAddress` | loopback address and port | Bound to loopback only, per FR-018 and D9 |
| `trustState` | `UNTRUSTED` or `TRUSTED` | Starts `UNTRUSTED`, per FR-007 |

**Rules.**

- A backend binds to loopback on its host. The client reaches it through an EEL tunnel. The session
  port is never opened on a public interface.
- A backend runs no project-supplied code while `trustState` is `UNTRUSTED`. This covers a build
  script and a project configuration file.
- Several backends run on one host at once and must not share a configuration directory or a system
  directory. FR-019 depends on this.

**States.** `NotProvisioned -> Provisioning -> Starting -> Ready -> Stopping -> Stopped`. A backend
in `Ready` accepts a connection. A backend in any other state refuses one and states its state.

---

## ThinClient

The local application. It is a product built from this repository.

| Field | Type | Rule |
|---|---|---|
| `productVersion` | build number | Required |
| `supportedProtocolVersions` | list of versions | Required. Ordered, most recent first |
| `settingsScope` | local | Holds no project source, per FR-051 and the P1 goal |

**Rules.**

- The client holds no copy of the project sources at any time.
- The client never writes a credential to a file. It uses the platform credential store.

---

## RemoteSession

A live association between one thin client and one backend.

| Field | Type | Rule |
|---|---|---|
| `id` | stable string | Unique. Survives a reconnection |
| `backendId` | Backend id | Required |
| `clientId` | platform `ClientId` | Supplied by the platform |
| `negotiatedVersion` | protocol version | Set once at connect. Never changes within a session |
| `status` | see states below | Required |
| `tokenRef` | credential store key | Revocable by the host owner, per FR-018 |
| `pendingEdits` | edit log | Survives a disconnection, per FR-015 |

**Rules.**

- The negotiated version is fixed for the life of the session. A version change requires a new session.
- A session that loses its connection keeps its identity and its pending edits for at least five
  minutes. FR-015 requires this.
- Revoking `tokenRef` ends the session at the next operation. It does not wait for a reconnection.

**States.**

```text
Connecting -> Negotiating -> Connected -> TemporarilyDisconnected -> Connected
                   │                              │
                   └──> Refused                   └──> Expired
```

- `Refused` is terminal. It carries a reason, a client version, and a backend supported range.
- `TemporarilyDisconnected` carries the time at which the next attempt runs.
- `Expired` is terminal. It occurs when the outage passes the retention window, and it reports what
  was lost rather than discarding it silently.

---

## ControllerSession

The platform supplies this entity. It is a `ClientSession` whose type is `ClientType.CONTROLLER`.

P1 adds no field. P1 adds the registration that Community lacks today, because
`ClientSessionManagerImpl` registers only a local session.

**Rules.**

- Every operation that a remote client causes runs under that client's `ClientId`.
- A per-client service resolves through `ClientAwareComponentManager`. P1 introduces no parallel
  registry.

---

## ForwardedPort

A network port on the host that the local machine can reach.

| Field | Type | Rule |
|---|---|---|
| `sessionId` | RemoteSession id | Required. A port dies with its session |
| `hostPort` | port number | Required |
| `localPort` | port number | Assigned locally. May differ from the host port |
| `origin` | `USER` or `DETECTED` | FR-016 requires both paths |

**Rules.**

- A forwarded port closes when its session ends. A leaked tunnel is a defect.
- A detected port is offered to the user. It is not forwarded without consent.

---

## ProtocolVersion

| Field | Type | Rule |
|---|---|---|
| `value` | string | Ordered. Compared as a whole, not by substring |

**Rules.**

- A backend supports at least the two most recent versions, per FR-057.
- A client offers its supported list, most recent first. The backend selects the first one that it
  supports.
- If no version is shared, the connection is refused before any project state is touched. The refusal
  names the client version and the backend range, which the FR-010 error rule requires.

---

## Validation summary

The rules below apply across every entity and come straight from the cross-cutting requirements.

| Rule | Source |
|---|---|
| No credential is stored outside the platform credential store | FR-008 |
| No credential appears in a log or a diagnostic report | FR-008, SC-013 |
| No project-supplied code runs before the user grants trust | FR-007, SC-015 |
| Every long operation reports progress and can be cancelled | FR-009 |
| A failed operation leaves no partial state | FR-009 |
| Every failure names the operation, the probable cause, and a next action | FR-010 |
