# Split coverage survey (T098)

**Date**: 2026-09-05
**Purpose**: state what a thin client can and cannot show today, so that P1.7's scope rests on
evidence rather than on the two gaps FR-014 happened to name.

Research decision D7 counted the FR-014 capabilities. This survey counts the platform.

## The counts

| | Modules |
|---|---|
| Paired: a backend and a frontend exist | **18** |
| A backend exists with no frontend | **14** |
| Refactoring: neither exists | 1 subsystem |

## Backend-only modules, classified

Not every backend-only module is a gap. Most are backend concerns with no user interface at all,
and a frontend for them would be meaningless.

### Legitimately backend-only, no work needed

| Module | Why |
|---|---|
| `intellij.platform.indexing.impl` | Indexing runs where the files are |
| `intellij.platform.kernel` | The entity database itself |
| `intellij.platform.rpc` | The transport |
| `intellij.platform.project` | The project model |
| `intellij.platform.progress` | Progress is reported through other surfaces |
| `intellij.platform.managed.cache` | A cache on the host |
| `intellij.platform.scopes` | Scope resolution needs the project model |
| `intellij.platform.lang.impl` | Language analysis runs on the host |
| `intellij.platform.polySymbols` | Symbol resolution runs on the host |

### Real gaps: user-facing, and a thin client shows nothing today

| Module | Surface | FR-014 |
|---|---|---|
| `intellij.platform.find` | Find and Replace, Find in Files | **Yes**, named by FR-014 |
| `intellij.platform.todo` | The TODO tool window | No |
| `intellij.platform.scriptDebugger` | Script debugger UI | No, the JVM debugger is paired |
| `intellij.platform.ide.updateChecker` | Update notifications | No |
| `intellij.platform.ide.internal` | Internal actions, developer only | No |

### Refactoring: no split at all

`platform/refactoring` exists as one module, `intellij.platform.refactoring`, with neither a backend
nor a frontend counterpart. FR-014 names refactoring, so this is the larger of the two named gaps.

## What this changes

The plan assumed two gaps. There are **two named by FR-014**, and **four more** that a user would
notice in a thin client: the TODO view, the script debugger, update notifications, and the internal
actions.

None of the four extra gaps blocks FR-014, so none blocks P1. They are recorded so that a later
slice sizes them rather than discovering them.

## Honest note on the size of the two named gaps

Splitting refactoring is not a module scaffold. The refactoring subsystem drives dialogs, previews,
conflict resolution and undo across every language. A split means deciding, for each of those, what
state crosses the wire and what runs where. The same is true of Find in Files at a smaller scale.

P1.7 therefore delivers the contract and the module structure. Routing the existing refactoring
machinery through it is the larger part, and it should be sized on its own rather than absorbed
into this slice.
