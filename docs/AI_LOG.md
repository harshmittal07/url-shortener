# AI usage log

Traceability for every AI-assisted step. Agents draft rows; **only the engineer fills Outcome and Rationale.**

Outcome values: **Accepted** (used as generated) · **Edited** (used after engineer changes) · **Rejected** (discarded; reason required).

## Pre-planning (before the build window)
| Date | Tool | What AI did | Engineer decision |
|---|---|---|---|
| 2026-10-05 | Claude (chat) | Presented options and trade-offs for architecture, stack, auth, redirects, API versioning, service decomposition, documentation approach | Made decisions D1–D12 (`docs/DECISIONS.md`); several AI recommendations were overridden (e.g. versionless API chosen over the suggested `/api/v1`) |

## Gate approvals
| Spec | Gate | Artifact | Approved by | Notes |
|---|---|---|---|---|
| 01 | Gate 1 (spec) | `specs/01-core-shortener/spec.md` | | |
| 01 | Gate 2 (plan) | `specs/01-core-shortener/plan.md` | | |
| 01 | Gate 3 (tasks) | `specs/01-core-shortener/tasks.md` | | |

## Task log
| Task | Agent | Role | Prompt summary (intent · constraints · acceptance) | Files | Generated | Agent's uncertainties | Outcome | Rationale |
|---|---|---|---|---|---|---|---|---|
| 01-T1 | Claude Code | Builder | | | | | | |
| 01-T1 | Codex | Reviewer | Review commit against spec, AGENTS.md, SECURITY.md | — | Findings list | | | |

## Rejections and corrections (highlights for the summary)
| # | Task | What the AI produced | Why it was wrong | What replaced it |
|---|---|---|---|---|
| 1 | | | | |
