---
name: spec-driven-dev
description: Spec-driven development workflow where a written specification, not code, is the source of truth. Use this skill whenever the user says "spec", "spec-driven", "SDD", "write a spec", "/spec", or wants to build a feature from a problem description, or fix or enhance existing code in a structured, reviewable way. Supports two entry modes - Mode A (start from a problem statement) and Mode B (start from existing code to fix or enhance). Make sure to use this skill when the user wants requirements, acceptance criteria, a plan, and tasks produced before any implementation, even if they don't say "spec-driven".
---

# Spec-Driven Development (SDD)

The spec is the source of truth. Code, tests, and tasks all trace back to it. Never write implementation code until the spec and plan are approved by the user.

## Step 0: Choose the mode

At the start, ask the user which mode they want, unless they already made it clear:

- **Mode A - Problem first:** "I'll describe a problem or idea." You turn it into a spec, plan, and tasks, then build it.
- **Mode B - Existing code:** "Here is existing code (file, folder, or bug). Fix or enhance it." You study the code, reverse-engineer its current behavior, write a spec of the *desired* behavior, and change only what the spec requires.

Also ask (or infer) whether Mode B is a **fix** (something is wrong) or an **enhancement** (something new is needed).

## Artifacts

Keep everything in `specs/<short-feature-name>/` in the repo:

| File | Purpose |
|---|---|
| `spec.md` | What and why. Behavior, acceptance criteria, edge cases. No implementation detail. |
| `plan.md` | How. Design, files to change, test strategy, risks. |
| `tasks.md` | Ordered, small, checkable tasks, each linked to acceptance criteria. |
| `current-behavior.md` | Mode B only. What the code does today, with evidence. |

Templates are at the bottom of this file.

## Workflow and approval gates

Stop at each gate and wait for the user's explicit approval ("approved", "go ahead", or edits) before continuing.

### Phase 1: Understand
- **Mode A:** Restate the problem. Ask up to 5 focused clarifying questions covering users, scope, constraints, and success criteria. If the user says to proceed, list assumptions instead of asking more.
- **Mode B:** Read the target code and its tests. Map entry points, data flow, dependencies, and existing test coverage. Reproduce the bug or exercise the current behavior. Write `current-behavior.md` with what you observed and how you know (test output, log, or code reference with file and line).

### Phase 2: Specify -> GATE 1
Write `spec.md`. Every requirement gets an ID (`R1`, `R2`...) and every acceptance criterion an ID (`AC1`...) in Given/When/Then form so it can be turned directly into a test. Mark unknowns as `[NEEDS CLARIFICATION]` and resolve them with the user before the gate. Show a short summary, then ask for approval.

In Mode B, the spec describes the *desired end state* and includes a **Delta** section: what changes versus current behavior, and what must stay exactly the same.

### Phase 3: Plan -> GATE 2
Write `plan.md`: approach, alternatives considered (with a one-line reason for the choice), files and modules affected, data or API changes, test strategy per acceptance criterion, risks, and rollback. Keep it consistent with existing architecture and conventions. Ask for approval.

### Phase 4: Tasks -> GATE 3
Write `tasks.md`: small tasks (roughly one commit each), ordered by dependency, each tagged with the ACs it satisfies. Tests come before the code they cover. Ask for approval.

### Phase 5: Implement (one task at a time)
For each task:
1. **Red:** write the failing test for the linked AC. In Mode B fixes, this is a test that reproduces the bug.
2. **Green:** make the smallest change that passes.
3. **Refactor:** clean up with tests green.
4. Run the relevant tests and lint, report real results, tick the task in `tasks.md`.
5. Pause briefly with a one-line status before the next task if the change is risky or large.

Mode B extra rule: before changing code that lacks tests, add **characterization tests** that lock in current behavior for anything the Delta says must not change.

### Phase 6: Validate and close
- Build a traceability table: `R -> AC -> test name -> task -> commit`. Any requirement without a passing test is called out.
- Run the full test suite and linters.
- If implementation revealed that the spec was wrong or incomplete, **update the spec first**, tell the user what changed and why, then continue. The spec must always match the shipped behavior.
- Summarize: what changed, what was verified, open items, and follow-ups.

## Rules

- No code before Gate 3 approval. Small exploratory reads and reproduction scripts are fine.
- Do not add behavior that is not in the spec. Propose it as a spec change instead.
- Keep scope tight. Record out-of-scope ideas under "Non-goals" or "Follow-ups".
- Never claim tests pass without running them. If something cannot be run, say so and give the exact command.
- If `dev-standards` is available, follow it for code quality, testing, and commit conventions.
- Be concise at each gate: summarize the artifact and point to the file rather than pasting it all.

## Templates

### spec.md
```markdown
# <Feature or fix name>
Mode: A (new) | B-fix | B-enhancement
Status: Draft | Approved

## Problem
<Who is affected, what is wrong or missing, why it matters.>

## Goals
- G1: ...

## Non-goals
- ...

## Requirements
- R1: The system shall ...
- R2: ...

## Acceptance criteria
- AC1 (R1): Given ..., when ..., then ...
- AC2 (R1): ...

## Edge cases and error handling
- ...

## Non-functional requirements
Performance, security, accessibility, observability, compatibility.

## Delta (Mode B only)
- Changes: ...
- Must stay the same: ...

## Open questions
- [NEEDS CLARIFICATION] ...
```

### plan.md
```markdown
# Plan: <name>
## Approach
## Alternatives considered
## Affected files and modules
## Data / API changes
## Test strategy (per AC)
## Risks and rollback
```

### tasks.md
```markdown
# Tasks: <name>
- [ ] T1 (AC1): Write failing test for ...
- [ ] T2 (AC1): Implement ...
- [ ] T3 (AC2, AC3): ...
```

### current-behavior.md (Mode B)
```markdown
# Current behavior: <area>
## Entry points and data flow
## Observed behavior (with evidence)
## Existing test coverage and gaps
## Reproduction steps (for bugs)
```
