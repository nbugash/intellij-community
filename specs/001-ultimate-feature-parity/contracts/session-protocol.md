# Contract: Session Protocol, version 1

**Feature**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md)
**Date**: 2026-09-04
**Status**: Draft for P1.3

FR-052 states that this document, and not any implementation, is the source of truth for the wire
contract between a thin client and a backend. A change to the wire behaviour changes this document
first.

This protocol is our own. It does not interoperate with, emulate, or derive from any proprietary
client or connection broker. FR-001, FR-002, and FR-052 require this.

---

## 1. Scope

The protocol carries one session between one thin client and one backend. It does not cover the
provisioning of a backend, which happens before a session exists and uses the host access layer
instead.

---

## 2. Transport and framing

| Property | Value |
|---|---|
| Carrier | A single duplex stream, reached through a host tunnel |
| Exposure | The backend binds to loopback on its host. The stream reaches it through the tunnel |
| Encryption | Required in transit, per FR-018 |
| Framing | Length-prefixed messages |
| Payload encoding | `kotlinx.serialization`, matching the platform RPC layer |

The service layer is the platform RPC layer. A service is an `@Rpc` interface that the backend
registers and the client resolves. This contract governs the session envelope around those services:
the handshake, the version rule, the failure model, and the reconnection rule.

---

## 3. Handshake

The handshake runs once per session, before any service call and before any project state is touched.

### 3.1 Client offer

The client sends one message containing:

| Field | Meaning |
|---|---|
| `supportedProtocolVersions` | Ordered list, most recent first. Must hold at least one entry |
| `clientProductVersion` | The client build number |
| `clientPlatform` | The operating system and architecture of the client |
| `sessionToken` | The credential that authorises this session |
| `requestedProjectPath` | The project on the host that the client wants |

The offer never carries a password or a private key. It carries a session token only.

### 3.2 Backend reply

The backend replies with exactly one of the two messages below.

**Accepted.**

| Field | Meaning |
|---|---|
| `negotiatedVersion` | The first client version that the backend supports |
| `backendProductVersion` | The backend build number |
| `sessionId` | The identity that survives a reconnection |
| `capabilities` | The optional features that this backend offers |

**Refused.**

| Field | Meaning |
|---|---|
| `reason` | A machine-readable code, see section 6 |
| `message` | Text for the user, naming a next action |
| `backendSupportedVersions` | The full supported range |

### 3.3 The version rule

- The backend selects the first entry of `supportedProtocolVersions` that it supports.
- The backend supports at least the two most recent versions. FR-057 requires this.
- If no entry is shared, the backend refuses with `VERSION_MISMATCH` and states both sides. The
  refusal happens before the backend opens the project.
- The negotiated version is fixed for the life of the session. To change it, open a new session.

---

## 4. Session lifetime

```text
Connecting -> Negotiating -> Connected -> TemporarilyDisconnected -> Connected
                   │                              │
                   └──> Refused (terminal)        └──> Expired (terminal)
```

**Reconnection.** A dropped connection does not end the session. The client retries with the same
`sessionId`. The backend keeps the session and its unsaved editor state for a retention window of at
least five minutes, as FR-015 requires. A reconnection inside the window resumes the session and
loses no edit. A reconnection after the window fails with `SESSION_EXPIRED`, and the failure reports
what was lost. It does not discard the work silently.

**Revocation.** The host owner revokes a session token at any time. The backend then ends the session
at its next operation. It does not wait for a reconnection attempt.

---

## 5. Trust

A backend starts in the untrusted state. It runs no project-supplied code, which includes a build
script and a project configuration file, until the user grants trust for that project. FR-007 and
SC-015 require this. Trust is per project, and it is not implied by a successful handshake.

---

## 6. Failure model

Every failure carries a code, a message for the user, and at least one next action. FR-010 requires
this. An opaque failure is a defect.

| Code | Meaning | Terminal |
|---|---|---|
| `VERSION_MISMATCH` | No shared protocol version | Yes |
| `PRODUCT_MISMATCH` | The client and the backend builds are incompatible | Yes |
| `AUTH_REJECTED` | The session token is invalid or revoked | Yes |
| `PROJECT_NOT_FOUND` | The requested path does not exist on the host | Yes |
| `PROJECT_LOCKED` | Another session already holds this project | Yes |
| `BACKEND_NOT_READY` | The backend is provisioning or starting | No |
| `SESSION_EXPIRED` | The retention window passed during an outage | Yes |
| `TRUST_REQUIRED` | The operation needs project trust that the user has not granted | No |

A refusal never reveals a credential, a token, or a path outside the requested project.

### 6.1 Codes that do not travel

`INSECURE_TRANSPORT` is a failure code, but no peer sends it. The client decides it before the
handshake, when it looks at the address it is about to connect to. The backend binds the loopback
address, so a plain text connection from the network never arrives.

The distinction matters for version negotiation. An older peer cannot read a code name it does not
know, so a code that travels can only be added in a new protocol version. A code that does not
travel can be added in the same version. `ContractEvolutionTest` holds this rule.

---

## 7. Rules that a change to this contract must respect

1. A new field must be optional, so that an older peer can ignore it.
2. A change that an older peer cannot ignore requires a new protocol version.
3. When a new version ships, the backend must still support the previous one, so that the two most
   recent versions always work together.
4. A removed field requires a new version. Silent removal is forbidden.
5. This document changes before the implementation does.

---

## 8. Open items for P1.3

- The retention window is specified as "at least five minutes". The exact value, and whether the host
  owner can configure it, is a P1.3 decision.
- The `capabilities` field has no defined vocabulary yet. It exists so that a later slice can add one
  without a version change.
- No latency measurement exists for this transport under the SC-002 target. P1.1 must measure it
  before P1.3 fixes the message shape.
