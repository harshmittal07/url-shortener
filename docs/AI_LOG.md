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
| 00-setup | Claude Code (CLI), Codex (IntelliJ AI Assistant) | Both | Verify project rules load; test secret-file read denial | — | Rule summaries | First session started before rules were added and correctly refused to guess | Accepted | Both agents load AGENTS.md (Claude cited §7–9; Codex stated severity order and read-only role). .env* read blocked via deny rule; shell attempt blocked by approval prompt; agent did not work around it. Residual risk: deny rules don't cover approved shell commands. |
| 01-T1 | Claude Code | Builder | | | | | | |
| 01-T1 | Codex | Reviewer | Review commit against spec, AGENTS.md, SECURITY.md | — | Findings list | | | |

## Rejections and corrections (highlights for the summary)
| # | Task | What the AI produced | Why it was wrong | What replaced it |
|---|---|---|---|---|
| 1 | | | | |

## Work sessions
| Session | Date | Start | End | Focus | Notes |
|---|---|---|---|---|---|
| 0 | 2026-10-05 | 21:17 | 23:30 | Planning and decisions (D1–D12) | Before the build; options explored with Claude chat, decisions made by me |
| 1 | 2026-10-05/06 | 23:30 | 02:15 | Environment setup, project rules, spec 01 to Gate 1 | About 30 minutes lost to Docker (Rosetta) and macOS folder permissions |
| 2 | 2026-10-07 | 05:51 | | Gates 2 and 3, build spec 01 | |

Gaps between sessions are planned breaks; the work was split around other commitments.
