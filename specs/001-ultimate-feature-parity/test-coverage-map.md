# Test coverage map

Constitution Principle 5, Testing, asks three things of every new feature: a headless-capable test,
the right fixture for the surface under test, and domain logic tested without mocking PSI or VFS.
This file records where the fork stands against each, task T125.

Counts are from the run on 2026-09-05: 202 tests across five modules, all passing.

## What covers what

### `intellij.platform.remoteDev.protocol` (68)

| Test | Covers |
| --- | --- |
| `SessionFramingTest` (9) | Length-prefixed frames. A frame claiming more bytes than the stream holds is an error, not a short read |
| `HandshakeTest` (7) | The session envelope, contract section 3 |
| `VersionNegotiationTest` (7) | Range negotiation, FR-057 |
| `ContractEvolutionTest` (5) | The pinned serialized name of every wire enum value |
| `SessionLifecycleTest` (8) | State transitions from connecting to ended |
| `ReconnectionTest` (8) | Resume within the window, expiry past it |
| `SessionTransportSecurityTest` (10) | TLS or loopback, and the textual loopback rule |
| `SecretRedactionTest` (9) | Both lines of defence: types that hide their own value, and `LogRedaction` |
| `SessionFailureReporterTest` (5) | Every failure code renders a message with a next action |

### `intellij.platform.remoteDev.backend` (36)

| Test | Covers |
| --- | --- |
| `HandshakeResponderTest` (7) | The host's answer as a pure function of its inputs |
| `SessionTokenRegistryTest` (8) | Digest storage, validation, revocation, expiry |
| `BackendTrustGateTest` (4) | The rule: which operations run project code |
| `UntrustedProjectTest` (4) | The enforcement, SC-015, with a real script and a positive control |
| `DocumentReplayTest` (6) | Replaying edits after a reconnect |
| `BackendDirectoryLayoutTest` (7) | Per-session directories |

### `intellij.platform.remoteDev.frontend` (19)

| Test | Covers |
| --- | --- |
| `FrontendSessionControllerTest` (5) | Client-side session state |
| `SessionReconnectorTest` (8) | Backoff and give-up |
| `HostLinkTest` (6) | Parsing a connection link, and refusing a malformed one |

### `intellij.platform.remoteDev.provisioning` (65)

| Test | Covers |
| --- | --- |
| `HostRegistryTest` (7) | Host records, FR-013 |
| `HostBootstrapTest` (8) | Bringing a host up |
| `AgentSelectionTest` (8) | Platform matching, and the mismatch message |
| `AgentDeploymentTest` (8) | Placing the agent on a host |
| `PortForwardingTest` (8) | Forwarding, and closing every port with the session |
| `FirstRunTimingTest` (6) | The first-run budget |
| `CredentialStorageTest` (8) | Secrets in the platform store, references in the host record |
| `AdminHostPolicyTest` (12) | Allowed hosts and the TLS requirement, FR-020 |
| `SshCommandQuotingTest` (8) | Shell quoting for the remote command. Runs everywhere, no host needed |
| `SshHostBootstrapTest` (7) | `SshHostBootstrap` against a real SSH host. Skipped unless an identity is named |

### `intellij.idea.community.build.thinClient` (14)

| Test | Covers |
| --- | --- |
| `LicenseValidatorTest` (6) | The allowed list, and stopping on an unrecognised licence |
| `SecretScannerTest` (8) | Credentials in shipped files, and the redaction marker as a non-finding |

## Against the three minimums

**Headless-capable: met.** All 202 tests are plain JUnit 5 with no UI and no IDE instance. They run
under `tests.cmd` in a few hundred milliseconds per module.

**No mocking of PSI or VFS: met, and structurally.** No mocking framework appears anywhere in the
five test modules. There is no Mockito and no MockK on any test classpath, so the rule cannot be
broken quietly. Where a test needs a collaborator it gets a real one, a small in-memory
implementation of a platform interface, or a lambda.

**The right fixture per surface: partly met, and this is the honest gap.**

Most tests above are unit tests, because most features above are domain logic that can be one. That
is the correct fixture for what they cover, and it is the reason the suite runs in under a second.

`SshHostBootstrapTest` is the exception, and the first integration test in the fork. It runs against
a real SSH host, uploads a real file, opens a real tunnel, and asserts through it. It is skipped
unless an identity file is named, so a checkout without a host still builds, and the skip was
verified rather than assumed: without the property all seven report as skipped and none as failed.

One of its assertions is worth naming. `an argument containing a semicolon does not run on the host`
is checked against a real shell, and it was confirmed by mutation: with the quoting removed, the
injected `touch` created its file. A quoting bug is the difference between running a command and
handing the host to whoever supplied the argument.

What still has no fixture is every surface that needs a running IDE: opening a real project, an RPC
call over a real connection, an editor. The blocker there is not the harness but the product: no
entry in `build/dev-build.json` builds a backend, so `SplitBackendStarter` exists and nothing can
launch it. Those are not untested by oversight; they are the deferred tasks
T048, T049, T089 through T097, and T099, and each carries its reason where it stands.

The one place the gap is visible inside a passing test is `UntrustedProjectTest`. It runs a real
executable script and asserts it does not run without trust, and it carries a control that runs the
same script with trust granted, so the assertion has teeth. What it drives is
`BackendOperationDispatcher`, not IntelliJ's own project-open path. It proves the gate refuses. It
does not prove that every future path to running project code goes through the gate. That second
proof needs a running IDE, and it belongs to T099.

## What would strengthen this next

The suite tests behaviour, not implementation, and it has no mocks to rot. The weakness is that it
is entirely one layer. The first integration test to write is not the largest one; it is the one
that opens a real untrusted project on a real backend and asserts the same thing
`UntrustedProjectTest` asserts today, because that is the assertion whose current proof is
narrowest.
