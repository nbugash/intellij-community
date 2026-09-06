<!--
SYNC IMPACT REPORT
==================
Version change: 1.0.0 -> 2.0.1
  2.0.0 -> 2.0.1 is a PATCH. Principle 3 named the Bazel flag `--local_ram_resources`, which Bazel
  9.1.0 rejects as unrecognised. Corrected to `--local_resources=memory=HOST_RAM*.75`, verified by a
  real build. The Verification Standards call an invented command a defect, so this had to be fixed
  rather than left as guidance.
Bump rationale: MAJOR. The principle set is reduced from nine to five and renumbered. Four principles
are removed and every remaining number changes meaning, so any artifact that cited a number by value
is invalidated. That is a backward incompatible governance change.

Modified principles (old -> new):
  - 2. Clean Architecture, Dependency Rule ... -> 1. Clean Architecture (absorbs the EP dependency
    inversion rule from the old principle 1)
  - 3. Clean Code ........................... -> 2. Clean Code (limits tightened: complexity 5 stays,
    function length drops from 50 to 30 lines, nesting depth 3 added)
  - 5. Threading Model Compliance ........... -> 4. Threading
  - 7. Testability and Coverage ............. -> 5. Testing (named fixtures, headless CI, domain
    isolation; the coverage percentage is not carried over)

Added sections:
  - 3. Resource Ceiling. New. It governs the agent's own resource use rather than the code.

Removed sections (recorded, not silently dropped):
  - 1. Extension Point First ... its dependency inversion half survives inside the new principle 1.
    The rule that a core platform class must not be modified directly does NOT survive. The ledger at
    docs/fork-platform-changes.md still exists and still records the one upstream change.
  - 4. Services Over Components ... no replacement. AGENTS.md does not cover this.
  - 6. Module Boundary Respect ... substantially covered by the new principle 1 and by the Bazel
    module graph, which rejects a cycle on its own.
  - 8. Build System ... no longer a principle. AGENTS.md still binds it independently: *.iml files
    are the source of truth, they generate BUILD.bazel, and a generated file must not be hand-edited.
  - 9. API Compatibility ... no replacement. Tasks T121 loses its backing principle.

Deviations from the supplied text, and why. Each is reversible on request.
  - Principle 3 ... MADE CONCRETE. "Never exceed 75% system memory" needs a number and a mechanism to
    be checkable. This host reports 61 GiB total, so the ceiling is stated as a computed share rather
    than a constant, and the principle names the commands that measure and cap it.
  - Principle 5 ... GENERALISED for non-PSI code. LightPlatformCodeInsightFixtureTestCase is correct
    for a PSI or editor feature and wrong for a wire protocol, which has no fixture. The principle
    names the fixture for PSI work and permits plain JUnit elsewhere.

Templates and dependent artifacts:
  - .specify/templates/plan-template.md ...... OK, no change needed. Its Constitution Check slot is
    generic.
  - .specify/templates/spec-template.md ...... OK, no change needed.
  - .specify/templates/tasks-template.md ..... UPDATED at 1.0.0 to defer to the constitution's testing
    principle by name rather than by number. Still correct at 2.0.0.
  - .claude/skills/speckit-*/SKILL.md ........ OK, no change needed. Every skill loads the
    constitution conditionally and uses no agent-specific naming.
  - specs/001-ultimate-feature-parity/plan.md  UPDATED. Re-evaluated against the five-principle set.
  - specs/001-ultimate-feature-parity/tasks.md UPDATED. T120, T123 and T125 cited old numbers. T121
    and T124 lost their backing principle and are marked as such rather than deleted.

Follow-up TODOs:
  - NOT COVERED: clean-room provenance and third-party licensing. The fork's legal premise rests on
    FR-001 to FR-004 in the spec, and no principle here enforces them. Raised for a decision rather
    than added, because the supplied set is deliberate.
  - NOT COVERED: API compatibility. T121 still creates api-dump.txt files, now without a principle
    behind it.
  - specs/001-ultimate-feature-parity/spec.md does not follow the AGENTS.md writing rule.
-->

# IntelliJ Community Fork Constitution

## Core Principles

### 1. Clean Architecture

**Rule**: A dependency MUST point inward, from framework and adapter code toward domain and use-case
code.

**Application**:

- The domain layer, meaning PSI models and core algorithms, MUST have zero imports from
  `platform-ui`, `platform-ide-core-ui`, or any `plugins/*` module.
- A use case or a controller orchestrates domain logic. It may depend on the domain and on
  abstraction interfaces. It MUST NOT depend on concrete infrastructure.
- An adapter, meaning user interface, file access, or network code, depends on the use cases and the
  domain. The reverse is forbidden.
- **An extension point is the dependency inversion mechanism.** The high-level module declares the
  contract and the low-level module implements it. Prefer an existing EP. Define a new EP only when
  no existing one covers the need. An extension implementation MUST be stateless, because the
  platform controls its lifetime. Hold runtime state in a service.
- A new interface goes in the innermost module that needs it.
- Enforce this through the Bazel module boundaries. A dependency edge that points outward is a
  violation, and a module cycle MUST NOT be hidden from the compiler with a runtime lookup.

*Rationale*: an EP also survives an upstream rebase, which a direct edit to a platform class does
not. The architectural rule and the maintenance rule point the same way here.

### 2. Clean Code

**Rule**: A new contributor MUST be able to read the code in under five minutes with no prior
context.

**Application**:

- A function MUST be 30 lines or fewer.
- Cyclomatic complexity MUST be 5 or lower.
- Nesting depth MUST be 3 or less. Use an early return or a guard clause instead of a nested
  `if-else`.
- No magic number and no magic string. Extract to a named constant or an enum.
- No dead code. Remove an unused import, parameter, method, or class.
- No commented-out code in a committed file. Version control holds the history.
- YAGNI. Do not add an abstraction, a configuration option, or an extension point for a future need.
- Follow the IntelliJ naming conventions: `camelCase` for a method, `PascalCase` for a class,
  `UPPER_SNAKE_CASE` for a constant. A name MUST reveal the intent, not the implementation.

**Exception to the complexity limit.** An exhaustive `when` or `switch` over a sealed type or an enum
is exempt. Such dispatch scores one point for each branch, but it adds no branching logic that a
reader must hold in their head, and the compiler proves that it is complete. Without this exception
the limit is unreachable: an exhaustive `when` over the eight-code `SessionFailure` enum in this
project's own contract scores 8.

**Excluded from measurement**: generated code, build descriptors, module and plugin XML, `*.iml` and
Bazel files, and a declaration with no body.

### 3. Resource Ceiling

**Rule**: The agent MUST NOT drive this machine above 75% of total system memory. When a task cannot
be completed inside that ceiling, the agent MUST refuse it and propose a narrower scope instead of
attempting it.

**Application**:

- Measure before a heavy operation. `free -g` reports total and available memory. `nproc` reports the
  core count. Compute the ceiling as 75% of the reported total rather than assuming a constant, so
  that the rule travels to another machine unchanged.
- Cap the build rather than trusting it. Bazel sizes its own parallelism from host RAM by default.
  Pass `--local_resources=memory=HOST_RAM*.75` to hold it under the ceiling, and narrow the target
  pattern before raising the cap. Quote the argument, because a shell expands the `*`. Note that
  `--local_ram_resources` does **not** work: Bazel 9.1.0 rejects it as unrecognised.
  The `HOST_RAM*.75` form states the ceiling as a share of the host, so it travels to another machine
  unchanged, which a fixed number does not.
- Prefer a targeted target pattern over a broad one. `//platform/remoteDev-protocol:remoteDev-protocol`
  over `//platform/...`. A broad pattern over this repository analyses thousands of targets and is
  the most likely way to breach the ceiling.
- Refuse rather than degrade. If the only way to finish is to exceed the ceiling, say so, name the
  operation that would breach it, and propose the smaller scope that fits. Do not start it and hope.
- A refusal under this principle MUST state the measured number, the ceiling, and the narrower scope
  offered. A refusal with no number is not evidence.

*Rationale*: an out-of-memory kill on a shared machine destroys unrelated work and leaves a partial
build that is hard to diagnose. This repository is large enough that an unbounded build is a real
risk, not a theoretical one. The ceiling is stated as a share rather than a fixed size so that it
holds on a smaller machine too.

### 4. Threading

**Rule**: Access to the data model, meaning PSI, VFS, and Project, MUST respect the read-write lock.

**Application**:

- Read from a non-EDT thread inside `ReadAction.compute { }`.
- Write only on the EDT, inside `WriteAction.run { }`.
- Use the coroutine dispatchers `Dispatchers.EDT` and `Dispatchers.Default`.
- Never call `SwingUtilities.invokeLater` or `invokeAndWait` for model access.
- A long read MUST be cancellable, so that a write is not starved.

*Rationale*: this is the most common source of a freeze and of a hard-to-reproduce data race in an
IntelliJ-based product. It matters more in this fork than in a typical codebase, because a remote
backend serves several clients whose requests arrive on threads that the IDE does not own.

### 5. Testing

**Rule**: A new feature MUST include tests, and those tests MUST run headless in CI with no real user
interface.

**Application**:

- Use `LightPlatformCodeInsightFixtureTestCase` for a PSI or editor feature, and
  `LightJavaCodeInsightFixtureTestCase` for a Java-specific feature. Plain JUnit is correct for code
  that touches neither PSI nor the editor, such as a wire protocol, which has no fixture to build.
- Test the domain logic in isolation. Do not mock PSI or VFS. Use an in-memory fixture.
- A test MUST verify observable behaviour, not internal structure. A test that breaks when a private
  method is renamed, while the behaviour is unchanged, is a defect in the test.
- Every measurable target in a specification MUST have a test that produces the measurement. A
  success criterion with no test that reports its number is a wish, not a criterion.
- Run a test with `./tests.cmd --module <module> --test <FQN>`. Name the module. A simple class name
  matches nothing.

## Verification Standards

A claim of completion MUST rest on evidence that someone else can reproduce.

- Run the affected tests with `./tests.cmd --module <module> --test <FQN>` and read the output.
- After a change to an `*.iml`, a `BUILD.bazel`, or a `.idea/` file, run
  `./build/jpsModelToBazelCommunityOnly.cmd`, then `./build/assertJpsModelToBazelCommunityPaths.sh`.
  `AGENTS.md` binds this independently of this document: an `*.iml` file is the source of truth and
  it generates `BUILD.bazel`, so a generated file MUST NOT be hand-edited.
- After a change to a Bazel or Starlark source, run `bazel run //:format.check`.
- Report the memory measurement for any operation that Principle 3 governs.
- Run the lint gate for Principle 2 and report the outliers by name.
- Never state that work is done, fixed, or passing without having run the command and read the
  output. "It should work" is not a status.
- A command written into a document MUST exist, or MUST be marked clearly as one that the work will
  create. An invented command is a defect.
- Report a failure plainly, with the output. A partial result reported as complete is worse than a
  failure reported honestly.

## Governance

This constitution takes precedence over habit, convenience, and schedule pressure. It does not take
precedence over an explicit instruction from the repository owner, over `AGENTS.md` on repository
mechanics, or over the law.

**Relationship to AGENTS.md.** `AGENTS.md` binds every artifact in this repository, including a
specification, a plan, a task list, a commit message, and a report. Where the two overlap, both
apply. Where they conflict, `AGENTS.md` wins on repository mechanics and this document wins on
process.

**Amendment procedure.** Propose the change in writing with its rationale. Record the version bump
and the reason. Update every dependent template and artifact in the same change, and list them in the
Sync Impact Report at the top of this file. An amendment that leaves a dependent artifact stale is
incomplete.

**Citation rule.** Cite a principle by number and name together, for example "Principle 4,
Threading". A number alone does not survive a renumbering, and this document has renumbered once.

**Versioning policy.** This document uses semantic versioning.

- MAJOR: a principle is removed, redefined, or renumbered in a way that invalidates prior compliance
  or prior citations.
- MINOR: a principle or a section is added, or existing guidance is materially expanded.
- PATCH: a clarification, a wording fix, or a non-semantic refinement.

**Compliance review.** Every plan MUST carry a Constitution Check that names the principles it was
evaluated against and states the result for each. A violation MUST be recorded in the plan's
Complexity Tracking table with the simpler alternative that was rejected and the reason. An
unrecorded violation is a review failure, not a design choice.

**Version**: 2.0.1 | **Ratified**: 2026-09-04 | **Last Amended**: 2026-09-04
