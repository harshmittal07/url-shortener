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
| 01 | Gate 1 (spec) | specs/01-core-shortener/spec.md | Harsh, 2026-10-06 | Approved after two review rounds. Round 1 added two DB users, identity schema, logging policy, request id in errors, body cap and Swagger local-only. Round 2 (fresh agent session) found 4 more gaps: AGENTS.md section 9 deviation, gitleaks wording, read-only filesystem, 413 counting. All resolved. Decided A1 (hash-only admin key), A2 (manual revocation for now), A10 (separate Flyway step). |
| 01 | Gate 2 (plan) | specs/01-core-shortener/plan.md | Harsh, 2026-10-07 | Approved with 5 decisions: JdbcClient over JPA (D14), new 500 internal-error code, AC17 moved into T4 so S-05 stays complete, .env.example kept human-owned, Boot 4 compatibility checked per task. Agent wrote the plan before showing the docs diff as instructed; no harm, diff reviewed before commit. |
| 01 | Gate 3 (tasks) | specs/01-core-shortener/tasks.md | Harsh, 2026-10-07 | 7 tasks, all 48 ACs assigned. T7 is polish and can be cut without losing a security control. |

## Task log
| Task | Agent | Role | Prompt summary (intent · constraints · acceptance) | Files | Generated | Agent's uncertainties | Outcome | Rationale |
|---|---|---|---|---|---|---|---|---|
| 00-setup | Claude Code (CLI), Codex (IntelliJ AI Assistant) | Both | Verify project rules load; test secret-file read denial | — | Rule summaries | First session started before rules were added and correctly refused to guess | Accepted | Both agents load AGENTS.md (Claude cited §7–9; Codex stated severity order and read-only role). .env* read blocked via deny rule; shell attempt blocked by approval prompt; agent did not work around it. Residual risk: deny rules don't cover approved shell commands. |
| 01-T1 | Claude Code | Builder | Implement T1 (foundation and gate) from the approved plan · test first, Boot 4 checks per task, no work beyond T1 · AC6, AC7, AC25 (arch), AC31, AC34, AC41; `./gradlew check` green; stop before commit | `build.gradle.kts`; `.gitignore` (`out/` → `/out/`); `app/` (main class moved, `SharedWebConfig`); `shared/id/`, `shared/web/` (`RequestIdFilter`, `ProblemDetails`, `SharedExceptionHandler`); `db/migration/V1–V3`; `application.yaml`; `docker/postgres/initdb/01-users.sh`; tests: `architecture/` (rules, self-test, fixtures), `migration/` (`MigrationsContainNoSecretsTest`, `SchemaIT`, `DatabasePrivilegesIT`), `shared/web/` (`RequestIdFilterTest`, `SharedExceptionHandlerTest`, `RequestIdIT`), `support/` (Testcontainers Postgres 16 with two users) | JPA → JDBC swap (D14); Spotless 8.10.3 (palantir 2.102.0), JaCoCo 0.8.15, ArchUnit 1.5.1; `test`/`integrationTest` split; migrations and users script as in plan §5; ArchUnit rules 1–6 plus a fixture self-test proving each rule detects a planted violation; 69 tests (45 unit, 24 integration, counting parameterized cases) | (1) Framework 4xx without a spec code (405, 406, 415) get a derived code such as `method-not-allowed`; not in the spec's error table. (2) ArchUnit rules 3 and 4b use `allowEmptyShould(true)` until link/redirect exist in T2. (3) JaCoCo domain gate passes vacuously: the only domain type is the `IdGenerator` interface. (4) Spring Security's default user still logs a generated password at WARN until T2 replaces it. (5) AC41's audit and ECS-log parts are verified in T2 and T7; T1 covers header, MDC and problem body. (6) Changed `.gitignore` outside the plan: its trailing `out/` rule silently ignored every `adapter/out/` source file; now `/out/` (root only) | | |
| 01-T1 | Codex | Reviewer | Review commit against spec, AGENTS.md, SECURITY.md | — | Findings list | | | |

## Rejections and corrections (highlights for the summary)
| # | Task | What the AI produced | Why it was wrong | What replaced it |
|---|---|---|---|---|
| 1 | D13 docs | My edit prompt quoted text that didn't match SECURITY.md | The edit silently didn't apply | Agent flagged the stale S-13 and section 9 row when asked about the gate; fixed in a follow-up commit. Lesson: check find-and-replace text against the file. |

## Work sessions
| Session | Date | Start | End | Focus | Notes |
|---|---|---|---|---|---|
| 0 | 2026-10-05 | 21:17 | 23:30 | Planning and decisions (D1–D12) | Before the build; options explored with Claude chat, decisions made by me |
| 1 | 2026-10-05/06 | 23:30 | 02:15 | Environment setup, project rules, spec 01 to Gate 1 | About 30 minutes lost to Docker (Rosetta) and macOS folder permissions |
| 2 | 2026-10-07 | 05:51 | | Gates 2 and 3, build spec 01 | |

Gaps between sessions are planned breaks; the work was split around other commitments.
