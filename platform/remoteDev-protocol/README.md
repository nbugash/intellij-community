# Session protocol

The wire contract between a thin client and a remote host.

The normative document is
[`session-protocol.md`](../../../specs/001-ultimate-feature-parity/contracts/session-protocol.md).
This file explains the shape of a session so the code is readable; where the two disagree, the
contract wins.

## What lives here

This module holds the contract and nothing else. It depends on no feature module, and no feature
module's types appear in it. A feature service, such as the editor or the debugger, declares its own
`@Rpc` interface in its own module. That keeps the dependency arrow pointing inward and keeps this
module compilable on its own.

## The life of a session

**Framing.** `SessionFraming` reads and writes length-prefixed frames. A frame that claims more
bytes than the stream holds is an error, not a short read, because a truncated frame that is
accepted becomes a message that means something other than what was sent.

**Transport.** Before anything is sent, `SessionTransportSecurity.verify` decides whether the
endpoint may be used at all. Two shapes pass: TLS on the socket, or plain text on a loopback
address, where the bytes never reach a network interface and an SSH tunnel carries them off the
machine. Loopback is decided from the text of the address and never by resolving a name, because a
name resolves wherever its owner points it.

**Handshake.** `SessionApi.handshake` runs once per session, before any other call, and it carries
the whole envelope: the protocol versions the client supports, its build, the project it wants. The
host answers with `HandshakeReply`, which is either a session or a refusal. `HandshakeResponder`
holds that decision, and it is a pure function of its inputs so it can be tested without a host.

**Versions.** `ProtocolVersions.supported()` is a range, not a number. Client and host negotiate the
highest version both understand. FR-057 requires negotiation rather than an equality check, which is
also why `libraries/rd` was rejected: it identifies a version by an exact hash and can only report
disagreement.

**Credentials.** A session is authorised by a `SessionToken`. The token hides its own value in
`toString`, so a log statement written by somebody who never considered redaction still cannot leak
it. The host stores a digest, never the token, in `SessionTokenRegistry`, and the owner can revoke
it at any moment, which FR-018 requires.

**Failure.** Every refusal is a `SessionFailure`, and every code carries a message key resolved
against `messages/RemoteDevProtocolBundle.properties`. `SessionFailureReporter` renders it. Each
message states a cause and one next action, per FR-010; a code with no message is a build failure,
not a blank dialog.

**End.** A session ends when the client disconnects, when the token is revoked, or when the
connection stays down longer than the session's limit. The last of those reports
`SESSION_EXPIRED` and names the limit.

## Changing the contract

`ContractEvolutionTest` pins the serialized name of every wire enum value. It fails when a name
changes, and it fails when a value is added.

That second failure is the point, and it is not noise. A `@Serializable` enum value that travels
cannot be deserialized by a peer that does not know the name, so adding one is a protocol change and
needs a version. A value that never travels does not. `INSECURE_TRANSPORT` is the worked example:
it is decided locally, before the handshake, so it needed no version bump. Contract section 6.1
records that reasoning. Add your own reasoning there before you edit the pinned set.
