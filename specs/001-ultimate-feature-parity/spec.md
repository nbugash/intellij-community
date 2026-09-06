# Feature Specification: Ultimate Feature Parity for IntelliJ IDEA Community Edition

**Feature Branch**: `feature/convert-to-ultimate-ed`

**Created**: 2026-09-04

**Status**: Draft — clarifications resolved 2026-09-04; ready for `/speckit-plan` scoped to P1

**Input**: User description: "Reimplement the Ultimate-only features in this community edition. Make sure you include all features including remote development"

---

## Scope Warning (read before planning)

This specification describes a **product programme, not a feature**. The commercial feature set
it targets represents an estimated several hundred person-years of accumulated engineering across
roughly a dozen independent product domains (databases, web frontend, application servers,
container orchestration, profiling, collaborative editing, distributed IDE hosting).

The specification is therefore written as **ten independently shippable slices** (P1–P10). Each
slice stands alone: implementing only P1 produces a usable product, and every later slice adds
value without requiring the ones after it. Any attempt to execute all ten as a single unit of
work will fail. Downstream `/speckit-plan` and `/speckit-tasks` runs SHOULD be scoped to one
priority slice at a time.

The programme ships through **two distribution channels** (see FR-053). P1 requires changes to the
platform itself and is therefore delivered as a fork of this repository, bundled with a thin client
this project owns and specifies (FR-052). Every other slice, P2–P10, is delivered as plugins that
install into an unmodified Community Edition *and* into the P1 fork. This split is a permanent
constraint on P2–P10, not a transitional one: no plugin slice may depend on anything the fork adds
to the platform, or the plugin channel breaks silently for stock-CE users.

**Non-negotiable legal constraint** (stated as a requirement, not a choice — see FR-001..FR-004):
all work is a clean-room reimplementation from public specifications and observable behaviour.
Decompiling, disassembling, or copying from the proprietary distribution is prohibited, as is
reusing its trademarks, branding, or bundled third-party commercial artifacts, or circumventing
any licensing mechanism.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Remote Development (Priority: P1)

A developer keeps their source code, toolchain, and compute on a remote machine (a cloud VM, a
lab workstation, a container, or WSL) and works on it from a lightweight local application. The
heavy work — indexing, compilation, static analysis, test execution — happens on the remote
machine. The local application shows the editor, the project view, and the tool windows, and
feels like a local IDE. The developer's laptop stays quiet and their large repository never has
to be cloned locally.

**Why this priority**: The user named it explicitly as a must-have, it is the single largest and
riskiest capability in the set, and it is architecturally foundational — the client/host split it
introduces constrains how every later slice exposes its UI. Building it last would force
rewriting the other nine. It is also the only slice with no partial substitute today.

**Independent Test**: Provision a remote host over SSH, open a project of at least 50,000 files
on it from the local client, edit a file, trigger completion, run the test suite, and set a
breakpoint — with the local machine never holding a copy of the sources. Delivers a complete
remote-work product on its own.

**Acceptance Scenarios**:

1. **Given** a reachable remote machine with credentials configured, **When** the developer
   supplies a host address and a project path, **Then** the backend is provisioned on the remote
   machine and a working editor window opens locally without a manual install step on the host.
2. **Given** an open remote session, **When** the developer types in the editor, **Then**
   keystrokes appear with latency indistinguishable from local editing on a connection with up to
   100 ms round-trip time.
3. **Given** an open remote session, **When** the network drops for up to 5 minutes, **Then** the
   session reconnects automatically and no unsaved edits are lost.
4. **Given** an open remote session, **When** the developer runs or debugs a configuration,
   **Then** the process executes on the remote machine and its console, breakpoints, variable
   inspection, and stepping all work from the local client.
5. **Given** a remote session, **When** the developer forwards a port for a running web
   application, **Then** the application is reachable from the local browser.

---

### User Story 2 - Database and SQL Tooling (Priority: P2)

A developer connects the IDE to the databases their application uses, browses schemas, writes and
runs SQL with completion and error highlighting, inspects and edits result sets in a grid, and
gets SQL awareness inside their application code — string literals containing queries are parsed,
validated against the live schema, and refactored along with the tables they reference.

**Why this priority**: It is the most frequently cited reason teams buy the commercial edition,
it is self-contained (no dependency on any other slice), and the table-rendering component already
exists in this repository, so the marginal work is drivers, introspection, and the SQL language —
a materially smaller lift than starting from nothing.

**Independent Test**: Connect to a running database, introspect its schema, author a multi-table
join with completion, execute it, edit a cell in the result grid, and commit the change. Delivers
standalone value to any developer with a database.

**Acceptance Scenarios**:

1. **Given** connection details for a supported database, **When** the developer creates a data
   source, **Then** the schema tree is populated and searchable within 30 seconds for a schema of
   1,000 objects.
2. **Given** an introspected data source, **When** the developer types a partial table or column
   name in a SQL editor, **Then** schema-aware completion offers matching names ranked by
   relevance.
3. **Given** a query returning at least 1,000,000 rows, **When** the developer executes it,
   **Then** the first page of results is displayed within 3 seconds and further pages load on
   scroll without loading the full result set into memory.
4. **Given** application source code containing an SQL string literal, **When** a referenced
   column is renamed in the schema, **Then** the IDE offers to update the literal.

---

### User Story 3 - Spring and JVM Framework Support (Priority: P3)

A developer working on a framework-based JVM application sees the framework's runtime model in the
IDE: dependency-injection wiring is navigable, configuration keys complete and validate, framework
annotations are understood by inspections and refactorings, and applications are launched with
framework-aware run configurations that surface live application state.

**Why this priority**: Spring is the dominant JVM application framework and the primary driver of
commercial-edition adoption among Java teams — the audience of this repository. It depends on
nothing but the existing JVM language support already present here.

**Independent Test**: Open a framework application, navigate from an injection point to its
definition, complete a configuration property key, and launch the app from a framework-aware run
configuration. Delivers value to any framework user independently.

**Acceptance Scenarios**:

1. **Given** an application with dependency injection, **When** the developer invokes navigation
   on an injection point, **Then** all candidate definitions are listed with their defining
   source location.
2. **Given** a configuration file, **When** the developer types a partial configuration key,
   **Then** valid keys for the declared dependencies are offered with their documentation and
   default values.
3. **Given** a misconfigured application, **When** the file is opened, **Then** unresolvable
   references and type mismatches are reported inline before the application is run.

---

### User Story 4 - JavaScript, TypeScript, and Web Frontend (Priority: P4)

A developer building the browser-facing half of their application gets the same depth of language
support for JavaScript and TypeScript as for JVM languages — type-aware completion, refactoring,
navigation, inspections — plus understanding of the major component frameworks, their template
syntax, and their build tooling, and the ability to debug code running in a browser from the
editor.

**Why this priority**: Full-stack teams are the largest commercial-edition segment. It is a large
but cleanly separable body of work with no dependency on P1–P3.

**Independent Test**: Open a TypeScript project with a component framework, rename a symbol across
template and script files, and debug a running page with breakpoints hit in original sources.

**Acceptance Scenarios**:

1. **Given** a typed project, **When** the developer renames an exported symbol, **Then** every
   reference across source files, templates, and re-exports is updated, and none outside the
   project is touched.
2. **Given** a component template, **When** the developer references a component property,
   **Then** completion and go-to-definition resolve into the component's script.
3. **Given** a running development server, **When** the developer starts a browser debug session
   and sets a breakpoint in original source, **Then** the breakpoint is hit and variables resolve
   to original names.

---

### User Story 5 - Containers and Orchestration (Priority: P5)

A developer manages container images, running containers, compose stacks, and cluster workloads
from inside the IDE: container definition files complete and validate, images build and run from
the editor, logs and shells are one click away, and cluster resources can be inspected, edited,
and their logs followed without leaving the IDE.

**Why this priority**: Containers are the default deployment substrate, and this slice becomes
substantially more valuable once remote hosts (P1) are in play — a container is one of the
remote-host kinds. Placed after the language slices because it is tooling around code rather than
comprehension of it.

**Independent Test**: Author a container definition with completion, build and run it from the
editor, attach to its logs, then connect to a cluster and follow a workload's logs.

**Acceptance Scenarios**:

1. **Given** a container definition file, **When** the developer types an instruction or a base
   image name, **Then** valid options are completed and invalid instructions are flagged inline.
2. **Given** a multi-service compose stack, **When** the developer starts it from the editor,
   **Then** every service's status and logs are shown in a single view and individual services can
   be restarted.
3. **Given** cluster credentials, **When** the developer opens the cluster view, **Then**
   workloads are listed by namespace and a workload's logs can be followed live.

---

### User Story 6 - HTTP Client and API Tooling (Priority: P6)

A developer defines HTTP requests as plain files stored in version control next to the code they
exercise, runs them from the editor, and inspects responses. API description documents complete
and validate, the endpoints an application declares are discoverable in one list regardless of
which framework declares them, and navigation runs both ways between a client call and the server
handler that answers it.

**Why this priority**: High value relative to implementation cost, and it composes with P3
(server-side endpoints) and P4 (client-side calls) once those exist.

**Independent Test**: Write a request file, execute it against a running service, assert on the
response, and navigate from the request to the handler that serves it.

**Acceptance Scenarios**:

1. **Given** a request file, **When** the developer executes a request, **Then** status, headers,
   timing, and a formatted body are shown, and the response is saved to a file for comparison.
2. **Given** a request referencing an environment variable, **When** the developer selects an
   environment, **Then** values are substituted, and secrets held in a private environment file
   are never written into version-controlled files.
3. **Given** an application declaring endpoints, **When** the developer opens the endpoint list,
   **Then** every declared endpoint is listed with its method, path, and declaring location.

---

### User Story 7 - Application Servers and Enterprise Java (Priority: P7)

A developer building a Jakarta EE application gets the platform's specifications understood by the
IDE — persistence, injection, validation, web tier — and can configure a local or remote
application server, deploy to it in one action, redeploy changed classes without a full restart,
and debug the deployed application.

**Why this priority**: A mature, slower-moving segment with a smaller but high-value audience.
The server-integration API already exists in this repository, so the work is adapters and
specification support rather than new architecture.

**Independent Test**: Configure a local application server, deploy an enterprise application to
it, hit a breakpoint in a deployed component, change a method body, and redeploy without
restarting the server.

**Acceptance Scenarios**:

1. **Given** a persistence model, **When** the developer writes a query in the persistence query
   language, **Then** entity and field names complete and unresolved names are flagged.
2. **Given** a configured server, **When** the developer runs the deployment configuration,
   **Then** the artifact is built, deployed, the server starts, and the application URL opens.
3. **Given** a running deployment in debug mode, **When** the developer changes a method body and
   triggers an update, **Then** the change takes effect without a full server restart.

---

### User Story 8 - Profiling and Runtime Diagnostics (Priority: P8)

A developer investigating slowness or memory pressure attaches a profiler to a running or
IDE-launched process, captures CPU and allocation data, and reads the results as flame graphs and
call trees whose frames navigate directly into source. Heap snapshots can be captured and explored
to find what is retaining memory.

**Why this priority**: Deep value but a narrower audience and a heavy dependency on
platform-specific native instrumentation, which makes it the highest-risk slice per unit of user
value. It also composes with P1 — profiling a remote process is the common real-world case.

**Independent Test**: Launch an application with profiling enabled, exercise it, stop the
recording, and navigate from the hottest frame to its source line.

**Acceptance Scenarios**:

1. **Given** an application launched with profiling, **When** the recording is stopped, **Then** a
   flame graph and call tree are shown and any frame in project sources navigates to its line.
2. **Given** a process consuming excessive memory, **When** the developer captures a heap
   snapshot, **Then** objects are grouped by retained size and a retention path to a root is shown
   for any selected object.
3. **Given** a remote session (P1), **When** profiling is started, **Then** the recording is
   captured on the remote machine and rendered locally.

---

### User Story 9 - Collaborative Development (Priority: P9)

A developer invites a colleague into their running IDE session over a shared link. The guest sees
the host's project, navigates it independently, edits files concurrently with visible cursors and
selections, and can be granted the ability to run, debug, and use a shared terminal — without
cloning the repository or installing the project's toolchain.

**Why this priority**: Valuable for pairing, interviewing, and support, but it is an
occasional-use capability rather than a daily one, and it depends on the same session-hosting
foundation as P1 — so it is materially cheaper once P1 exists.

**Independent Test**: Host a session, join it from a second machine, edit the same file from both
sides simultaneously, and confirm both converge on identical content.

**Acceptance Scenarios**:

1. **Given** a hosted session, **When** a guest opens the invitation link, **Then** they join
   without a local copy of the sources and see the host's project tree.
2. **Given** two participants editing the same file, **When** both type simultaneously, **Then**
   both converge on identical content and each sees the other's cursor and selection.
3. **Given** a guest with read-only permission, **When** they attempt to edit or run, **Then** the
   action is refused and the host is not affected.

---

### User Story 10 - Diagrams and Remaining Framework Coverage (Priority: P10)

A developer visualises structure — class hierarchies, module dependencies, persistence models,
bean graphs — as navigable diagrams, and gets IDE support for the remaining framework and
technology integrations that round out the commercial feature set.

**Why this priority**: The long tail. Each item is individually small and none blocks anything
else, so this slice absorbs residual scope without holding up the rest.

**Independent Test**: Open a class hierarchy as a diagram, expand a node, and navigate from a
diagram element to its source.

**Acceptance Scenarios**:

1. **Given** a class with subtypes, **When** the developer opens it as a diagram, **Then** the
   hierarchy renders and any node navigates to its declaration.
2. **Given** a rendered diagram, **When** the developer applies a filter, **Then** only matching
   nodes and their connecting edges remain visible.

---

### Edge Cases

- **Remote host is unreachable mid-operation**: an in-flight indexing or build operation on a
  dropped connection must not corrupt the remote project state, and the client must show what was
  lost rather than silently discarding it.
- **Version skew**: a local client connects to a remote backend of a different version. The
  mismatch must be detected before the session opens and either reconciled automatically or
  refused with an actionable message — never allowed to fail as undefined behaviour mid-session.
- **Concurrent sessions on one project**: two clients open the same remote project directory
  simultaneously. Either the second is refused with a clear reason, or both are supported with
  conflict resolution — silent index corruption is not acceptable.
- **Untrusted project content**: a project opened remotely or received through a collaborative
  session contains build scripts or configuration that execute code. Execution must not occur
  before explicit trust is granted.
- **Credential handling**: database passwords, cluster credentials, server credentials, and
  session invitation links must never be written to version-controlled files or logs, and must be
  revocable.
- **Very large result sets and heap snapshots**: artifacts larger than available memory must
  stream or page rather than exhaust memory.
- **Missing proprietary third-party components**: for databases and application servers whose
  drivers or adapters cannot be redistributed, the product must degrade to a clear
  user-supplied-component flow rather than failing opaquely.
- **Guest revocation mid-edit**: a collaborative guest's access is revoked while they hold
  unsaved changes. Host state must remain consistent and the guest must be told what happened.
- **Channel version skew**: a plugin slice built against one platform version is installed on a
  materially older or newer host — stock Community Edition or the P1 fork. The mismatch must be
  refused at load time with an actionable message, never surfaced as an arbitrary mid-use failure.
- **Fork-only capability absent**: a plugin slice that can take advantage of a fork-only platform
  capability is installed on stock Community Edition. It must detect the absence and degrade to
  defined behaviour, not assume the capability exists.
- **Protocol version negotiation failure**: a thin client and a backend share no supported protocol
  version. The connection must be refused before any project state is touched, naming both versions
  and the supported range.
- **Slice absence**: with only some slices implemented, features that reference an absent slice
  (for example, endpoint navigation with no web framework support) must be inert rather than
  broken.

---

## Requirements *(mandatory)*

### Legal and Provenance Requirements (non-negotiable)

- **FR-001**: All functionality MUST be implemented clean-room from publicly available
  specifications, documentation, and externally observable behaviour. Decompiling, disassembling,
  or reproducing code from the proprietary commercial distribution MUST NOT occur.
- **FR-002**: The product MUST NOT use the commercial product's name, logo, or other trademarks in
  its branding, and MUST NOT present itself as that product or as an official build of it.
- **FR-003**: The product MUST NOT bundle, redistribute, or require circumvention of any
  proprietary artifact or licensing mechanism belonging to the commercial distribution or to
  third-party vendors.
- **FR-004**: Every third-party component MUST carry a license compatible with the repository's
  license, and components that do not MUST be loaded at runtime from a user-supplied location
  rather than distributed.

### Cross-Cutting Requirements

- **FR-005**: Each priority slice MUST be independently installable and independently removable,
  and the product MUST remain functional with any subset of slices present. Slices delivered as
  plugins MUST install and function identically on an unmodified Community Edition and on the P1
  fork.
- **FR-006**: Every feature that presents a user interface MUST function identically whether the
  project is local or hosted remotely (P1), or MUST declare itself unavailable remotely with a
  stated reason.
- **FR-007**: The product MUST NOT execute project-supplied code — build scripts, configuration,
  or plugins — before the user has explicitly granted trust to that project.
- **FR-008**: All credentials and secrets MUST be stored in the platform's existing secure
  credential storage, MUST NOT be written to project files that are under version control, and
  MUST be redacted from logs and diagnostic reports.
- **FR-009**: Every long-running operation MUST report progress, MUST be cancellable, and MUST
  leave no partial state that prevents the operation from being retried.
- **FR-010**: Failures MUST report the failing operation, the probable cause, and at least one
  concrete next action; opaque failures MUST NOT be surfaced to users.

### Remote Development (P1)

- **FR-011**: Users MUST be able to open a project located on a remote machine and work in it from
  a local client without holding a copy of the sources locally.
- **FR-012**: The system MUST provision and start the remote backend automatically given only host
  access and a project path, with no prior manual installation on the host.
- **FR-013**: The system MUST support at least the following host kinds: a machine reachable over
  SSH, a Linux environment on the local Windows machine, and a container.
- **FR-014**: Editing, completion, navigation, search, refactoring, version control, run, debug,
  test execution, and terminal access MUST all be available in a remote session.
- **FR-015**: The system MUST detect connection loss, reconnect automatically without user action
  for outages up to 5 minutes, and preserve unsaved editor state across the reconnection.
- **FR-016**: The system MUST forward network ports from the remote host to the local machine on
  user request, and MUST detect and offer to forward ports opened by processes it launched.
- **FR-017**: The system MUST detect a version mismatch between client and backend before opening
  a session and MUST either reconcile it or refuse with an actionable message.
- **FR-018**: All traffic between client and backend MUST be encrypted in transit, and session
  credentials MUST be revocable by the host owner.
- **FR-019**: Multiple concurrent sessions against the same host MUST be supported without
  interference between their projects.

### Database and SQL Tooling (P2)

- **FR-020**: Users MUST be able to define data sources for databases and browse their introspected
  schemas.
- **FR-021**: The system MUST provide schema-aware editing for SQL — completion, resolution,
  validation, and formatting — with dialect awareness per data source.
- **FR-022**: The system MUST execute statements and present results in a navigable, filterable,
  sortable grid supporting in-place editing with explicit commit.
- **FR-023**: The system MUST page result sets so that memory use does not grow with total result
  size.
- **FR-024**: The system MUST recognise SQL embedded in application source code and apply the same
  resolution, validation, and rename support to it.
- **FR-025**: The system MUST support at least the following open-source databases out of the box:
  PostgreSQL, MySQL, MariaDB, SQLite, and H2. Databases whose drivers cannot be redistributed MUST
  be usable via a user-supplied driver.
- **FR-026**: The system MUST export result sets to at least delimited-text, JSON, and SQL insert
  formats.

### Framework Support (P3, P7, P10)

- **FR-027**: The system MUST model each supported framework's runtime wiring and make it
  navigable from source in both directions.
- **FR-028**: The system MUST complete, validate, and document framework configuration keys based
  on the dependencies actually declared by the project.
- **FR-029**: The system MUST report framework misconfiguration as inline problems before the
  application is run.
- **FR-030**: The system MUST provide framework-aware run configurations that launch applications
  with the framework's expected lifecycle.
- **FR-031**: The system MUST support deploying to, and debugging against, local and remote
  application servers, including updating changed classes without a full server restart.

### Web Frontend (P4)

- **FR-032**: The system MUST provide type-aware completion, navigation, refactoring, and
  inspections for JavaScript and TypeScript across a project's files.
- **FR-033**: The system MUST understand the template syntax of the major component frameworks and
  resolve references between template and component code.
- **FR-034**: The system MUST debug code running in a browser, mapping breakpoints and variables
  back to original sources.
- **FR-035**: The system MUST run and report results from the project's configured JavaScript test
  runner and linter, presenting failures as navigable source locations.

### Containers and Orchestration (P5)

- **FR-036**: The system MUST provide completion and validation for container and orchestration
  definition files.
- **FR-037**: The system MUST build, run, stop, and inspect containers and multi-service stacks
  from the editor, including log access and an interactive shell.
- **FR-038**: The system MUST connect to a cluster, list its workloads by namespace, follow logs,
  and apply edited resource definitions.

### HTTP and API Tooling (P6)

- **FR-039**: Users MUST be able to define HTTP requests in version-controllable plain-text files
  and execute them from the editor.
- **FR-040**: The system MUST support named environments with variable substitution, keeping
  secret-bearing environments outside version control.
- **FR-041**: The system MUST present responses with status, headers, timing, and formatted body,
  and MUST persist responses for comparison across runs.
- **FR-042**: The system MUST list all endpoints an application declares, across frameworks, and
  navigate between a client call and its server handler.
- **FR-043**: The system MUST validate API description documents and generate requests from them.

### Profiling (P8)

- **FR-044**: The system MUST attach to a running process, or launch one with profiling enabled,
  and capture CPU and allocation profiles.
- **FR-045**: The system MUST render captured profiles as flame graphs and call trees whose frames
  navigate to source.
- **FR-046**: The system MUST capture and explore heap snapshots, showing retained sizes and a
  retention path for any selected object.
- **FR-047**: Profiling MUST work against processes on a remote host when P1 is present.

### Collaborative Development (P9)

- **FR-048**: A host MUST be able to invite guests into a running session by link, and MUST be able
  to revoke any guest's access at any time.
- **FR-049**: Guests MUST be able to navigate and edit concurrently, with all participants
  converging on identical file content and each seeing the others' cursors and selections.
- **FR-050**: The host MUST be able to grant or deny each guest, individually, the ability to edit,
  to run and debug, and to use a shared terminal, with denied actions refused rather than silently
  ignored.
- **FR-051**: Guests MUST NOT require a local copy of the sources or the project's toolchain.

### Resolved Decisions

Both questions below were open in the first draft and were answered by the stakeholder on
2026-09-04. They are recorded here as binding requirements, with the rejected alternatives and the
reasoning retained so that later readers do not silently relitigate them.

- **FR-052**: The remote development client MUST be a thin client owned and specified by this
  project, connecting only to backends produced by this project. It MUST NOT interoperate with,
  emulate, or reverse-engineer any proprietary third-party thin client or connection broker.
  The session wire protocol MUST be specified in a written, versioned document maintained in this
  repository, and that document — not any implementation — is the protocol's source of truth.
  *Rejected*: wire compatibility with the proprietary client, which would require reverse
  engineering an undocumented and unstable protocol, breaking on every vendor release and
  conflicting directly with FR-001.
  *Consequence*: the thin client is a first-class deliverable of P1, not a dependency. Its user
  interface is in scope and its cost must be planned for.

- **FR-053**: The product MUST be distributed through two channels: P1 as a forked IDE product
  built from this repository and bundled with the thin client, and P2–P10 as independently
  versioned plugins installable into both an unmodified Community Edition and the P1 fork.
  *Rejected*: a single forked product for everything, which would deny stock-CE users every slice
  and multiply the upstream-merge burden across all ten; and a plugins-only model, which cannot
  express P1's client/host split from plugin space.
  *Consequence*: P1 owns a permanent upstream-merge obligation, and P2–P10 are permanently
  constrained to public extension points. See FR-054 through FR-057.

### Distribution and Compatibility Requirements

- **FR-054**: The P1 fork MUST track upstream Community Edition releases and MUST be rebasable onto
  each upstream release, with platform modifications kept minimal, isolated, and individually
  documented so that each can be evaluated for upstream contribution.
- **FR-055**: Each plugin slice MUST declare the minimum platform version it supports and MUST
  refuse to load, with an actionable message, on a platform older than that version — it MUST NOT
  fail at an arbitrary point during use.
- **FR-056**: Plugin slices MUST depend only on extension points available in an unmodified
  Community Edition. A plugin MUST NOT require any platform modification introduced by the P1 fork.
  Where a slice benefits from a fork-only capability, it MUST detect that capability at runtime and
  degrade to defined behaviour without it.
- **FR-057**: The session wire protocol MUST carry an explicit version, and client and backend MUST
  negotiate a mutually supported version at connection time or refuse the connection with a stated
  reason. A backend MUST support at least the two most recent protocol versions.

---

### Key Entities

- **Remote Session**: A live association between one local client and one backend running on a
  host. Holds connection state, the project being worked on, participants, and forwarded ports.
  Survives transient disconnection.
- **Host**: A machine or environment capable of running a backend — reachable over SSH, a local
  Linux environment, or a container. Has an address, credentials, capabilities, and a set of
  provisioned backends.
- **Backend**: A provisioned runtime on a host serving exactly one project to one or more clients.
  Has a version that must be compatible with its clients.
- **Thin Client**: The local application this project ships and owns. Renders the editor and tool
  windows for a Remote Session, holds no project sources, and speaks only the Session Protocol.
  Versioned independently of the backend it connects to.
- **Session Protocol**: The versioned wire contract between Thin Client and Backend, defined by a
  written specification in this repository rather than by any implementation. Every connection
  negotiates a mutually supported version before a session opens.
- **Distribution Channel**: One of the two ways a slice reaches users — the forked product (P1) or
  the plugin marketplace (P2–P10). Determines which platform modifications a slice may rely on and
  which platform versions it must support.
- **Data Source**: A named, credentialed connection to a database, plus its introspected schema and
  a dialect. Owns the driver it uses, which may be user-supplied.
- **Schema Model**: The introspected structure of a data source — catalogs, schemas, tables,
  columns, keys, routines — used to resolve references in SQL, whether in a SQL file or embedded in
  source code.
- **Framework Model**: The IDE's understanding of one framework's runtime wiring for one project —
  the components it declares, how they are configured, and how they reference each other. Derived
  from the project's declared dependencies and source.
- **Endpoint**: A declared request handler — its method, path, declaring source location, and the
  framework that declares it. Framework-independent, so that endpoints from different frameworks
  appear in one list.
- **Deployment Target**: A configured destination an artifact can be deployed to — an application
  server, a container runtime, or a cluster — with its credentials, lifecycle, and deployed
  artifacts.
- **Request Collection**: A version-controlled set of HTTP request definitions, plus the named
  environments that supply their variables. Secret-bearing environments are excluded from version
  control.
- **Profiling Snapshot**: A captured recording of a process's CPU, allocation, or heap state, bound
  to the source it was captured against so its frames remain navigable.
- **Collaboration Participant**: A person in a session, with an identity, a live cursor and
  selection, and an explicit permission set covering editing, execution, and terminal access.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer with only a host address and a project path reaches a working editor on a
  remote 50,000-file project in under 10 minutes on first use, with no manual installation
  performed on the host.
- **SC-002**: In a remote session over a 100 ms round-trip connection, 95% of keystrokes appear in
  under 50 ms and 95% of completion popups appear in under 300 ms.
- **SC-003**: A remote session survives 100% of network interruptions up to 5 minutes with zero
  loss of unsaved edits, measured over at least 100 induced interruptions.
- **SC-004**: A developer connects a database, browses its schema, and runs a query in under 3
  minutes on first use.
- **SC-005**: A query returning 1,000,000 rows displays its first page in under 3 seconds, and
  memory use while scrolling stays flat regardless of total result size.
- **SC-006**: On a reference framework application, navigation from any injection point to its
  definition is correct in at least 95% of cases, verified against a hand-labelled corpus.
- **SC-007**: Configuration-key completion offers the correct key within the top 3 suggestions in at
  least 90% of cases on a reference corpus.
- **SC-008**: Rename of an exported symbol in a typed frontend project updates 100% of in-project
  references and 0 out-of-project ones, verified across a corpus of at least 20 real projects.
- **SC-009**: A developer authors, runs, and inspects an HTTP request in under 2 minutes on first
  use, with no secret written to a version-controlled file.
- **SC-010**: A profiling recording of a 60-second workload renders in under 10 seconds, and every
  frame in project sources navigates to the correct line.
- **SC-011**: Two collaborating participants editing the same file concurrently converge on
  identical content in 100% of trials, across at least 1,000 randomised concurrent-edit trials.
- **SC-012**: Every priority slice can be installed and uninstalled independently, with the product
  fully functional in all tested subsets and no feature failing opaquely due to an absent slice.
- **SC-013**: No credential appears in any log, diagnostic report, or version-controlled file, and
  every credential is revocable — verified by automated scanning of all produced artifacts.
- **SC-014**: 100% of distributed third-party components carry a license compatible with the
  repository's license, verified automatically on every build.
- **SC-015**: No project-supplied code executes before trust is explicitly granted, verified by an
  automated test opening untrusted projects containing executable build configuration.
- **SC-016**: Every plugin slice installs and passes its full acceptance suite on both an unmodified
  Community Edition and the P1 fork, verified automatically on every release of that slice.
- **SC-017**: The P1 fork rebases onto each upstream Community Edition release with its platform
  modifications applying cleanly or with documented, reviewed conflict resolution, within 10
  working days of that upstream release.
- **SC-018**: A thin client and backend differing by one protocol version connect successfully in
  100% of trials; differing by more than the supported range, they refuse with a message naming both
  versions in 100% of trials.

---

## Assumptions

- **All ten slices are in scope, delivered in priority order.** The user asked for "all features".
  This is read as a programme-level goal, not as a single deliverable, and P1 is placed first
  because the user named remote development explicitly.
- **The target is parity with the commercial Java IDE's feature set**, not with the vendor's other
  commercial IDEs, except where those overlap (database tooling and web frontend support ship in
  the Java IDE and are therefore in scope).
- **The thin client's user interface is in scope and is built by this project** (FR-052). Its cost
  is a substantial fraction of P1 and must not be treated as incidental to the backend work.
- **The plugin channel targets the current and previous stable Community Edition releases.**
  Supporting a wider range of platform versions is out of scope for the first release of each slice.
- **Behavioural parity, not pixel or code parity.** The goal is that a user can accomplish the same
  tasks, not that dialogs or internals match.
- **Existing repository assets are reused rather than rebuilt** where they already exist — the
  vendored reactive-distributed protocol library, the table/grid component, the remote-server
  integration API, the partial remote-development utilities, and the existing language and VCS
  support.
- **Databases and application servers whose drivers or adapters cannot be redistributed are
  supported through a user-supplied-component flow**, following the established practice of
  open-source database tools. Only components with compatible licenses are distributed.
- **The Linux-on-Windows host kind and the container host kind cover the same use cases as a
  general local-virtualisation host**, so a separate local-VM host kind is out of scope for the
  first release of P1.
- **Cloud-hosted workspace brokers, vendor account systems, and subscription or licensing
  infrastructure are out of scope.** Hosts are machines the user already controls.
- **AI-assisted coding features are out of scope**, being a separately licensed product rather than
  part of the commercial IDE's feature set.
- **Mobile and game-engine tooling are out of scope**, being separate products.
- **Each slice will receive its own `/speckit-plan` and `/speckit-tasks` cycle.** This document is
  the programme-level specification, not an implementable unit of work.
