---
name: dev-standards
description: Engineering standards and workflow for any coding task - writing, changing, reviewing, or debugging code. Use this skill whenever the user asks to implement a feature, fix a bug, refactor, write or improve tests, review a diff, or prepare a commit or PR, even if they don't mention "standards". It enforces a clarify-plan-test-implement-verify loop, test-first quality gates, clean code conventions, and a definition of done.
---

# Development Standards

Follow this workflow on every coding task. The goal is small, verified, reviewable changes, not the fastest possible diff.

## 1. Clarify before coding

- Restate the task in one or two sentences, including the expected behavior and acceptance criteria.
- If requirements are ambiguous or a wrong guess would be costly, ask one focused question. Otherwise state your assumptions and proceed.
- Read the surrounding code first: existing patterns, naming, package layout, test style. Match them rather than introducing new ones.

## 2. Plan

- For anything beyond a trivial change, give a short plan before editing: files to touch, approach, risks, and how it will be tested.
- Prefer the smallest change that solves the problem. Flag scope creep and list it as a follow-up instead of doing it.
- If there are two reasonable designs, name both and recommend one with a one-line reason.

## 3. Test first (or at least test alongside)

- Write or update a failing test that captures the requirement or the bug, then implement until it passes.
- Follow the test pyramid: many fast unit tests, fewer integration tests, very few end-to-end tests.
- Every bug fix ships with a regression test that fails without the fix.
- Cover: happy path, boundaries, null/empty input, error paths, and any concurrency or ordering concerns.
- Tests must be deterministic and independent. No sleeps, no shared mutable state, no reliance on execution order, and no real network or clock without an injected fake.
- Name tests by behavior, for example `rejectsOrderWhenStockIsInsufficient`, and use Arrange-Act-Assert structure.
- Mock only at architectural boundaries (HTTP, DB, message brokers). Do not mock the class under test or trivial value objects.

## 4. Implement

Code quality:
- Single responsibility per class and method. Keep methods short and at one level of abstraction.
- Names reveal intent. Avoid abbreviations, magic numbers, and boolean flag parameters.
- Prefer immutability, constructor injection, and explicit dependencies. Avoid static state and hidden singletons.
- Handle errors deliberately: no swallowed exceptions, no generic `catch (Exception)` without a reason, and include useful context in messages. Never log secrets or personal data.
- Validate input at boundaries. Fail fast on invalid state.
- No dead code, commented-out code, or unresolved TODOs without a ticket reference.
- Comments explain why, not what.

Design:
- Keep layers separate (API/controller, service/domain, persistence). No business logic in controllers or repositories.
- Follow existing dependency direction; do not introduce cycles.
- Keep public APIs backward compatible unless the task says otherwise. Call out any breaking change explicitly.

Security and reliability:
- Parameterized queries only. No string-built SQL.
- No hard-coded credentials or environment-specific values. Use configuration.
- Consider timeouts, retries with backoff, idempotency, and resource cleanup for anything that does I/O.

## 5. Verify before declaring done

Run these and report the actual results. Never claim tests pass without running them.

1. Build and compile with no new warnings.
2. Run the relevant tests, then the full suite for the module if the change is shared code.
3. Run the linter and formatter (Checkstyle, SpotBugs, ktlint, detekt, or whatever the project uses).
4. Review your own diff for leftover debug code, unrelated changes, and missed edge cases.

If something cannot be run in this environment, say so plainly and list the exact command the user should run.

## 6. Definition of done

- [ ] Behavior matches the acceptance criteria
- [ ] New or changed logic has tests, including a regression test for bug fixes
- [ ] All tests, lint, and build checks pass
- [ ] No unrelated changes in the diff
- [ ] Public behavior changes are documented (README, Javadoc/KDoc, changelog, or API spec)
- [ ] Assumptions, trade-offs, and follow-ups are summarized for the reviewer

## 7. Commits and reviews

- Small, atomic commits. Message format: `type(scope): imperative summary`, for example `fix(orders): reject checkout when stock is zero`. Add a short body explaining why.
- PR description covers: what changed, why, how it was tested, risks, and rollback notes.
- When reviewing code, order feedback by severity (correctness and security, then design, then style) and suggest a concrete fix for each issue.

## 8. Debugging approach

- Reproduce first, ideally as a failing test.
- Form a hypothesis, then confirm it with evidence (logs, debugger, minimal repro) before changing code.
- Fix the root cause, not the symptom. Explain the cause in one or two sentences.

## 9. How to communicate

- Lead with the outcome, then the details. Keep summaries short and specific.
- Be honest about uncertainty and about anything you did not verify.
- Do not refactor or reformat unrelated code in the same change.
