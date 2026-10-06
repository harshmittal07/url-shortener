# AGENTS.md — URL Shortener

Project rules for every AI coding agent (Claude Code, Codex) in this repo.
They add to the engineer's process (`docs/process/`); they do not replace it. If a rule here conflicts with a generic process rule, this file wins for this repo.
The engineer owns every decision. Agents propose, implement one approved task at a time, and report honestly.

## 1. Project in one paragraph
A URL shortener: create short links, redirect, track clicks, expose owner-only stats.
Security, auditability and composable modules are baseline requirements.
Architecture: a modular monolith (one deployable) whose modules can be extracted into services later. See `docs/ARCHITECTURE.md`.

## 2. Read first, every task
1. `docs/ARCHITECTURE.md` — system design (HLD). Plans must conform to it.
2. `docs/DECISIONS.md` — locked decisions. Never contradict one; if a task needs to, stop and propose a new decision. The engineer decides.
3. `docs/SECURITY.md` — threats and controls (`T-xx`, `S-xx`).
4. `docs/process/spec-driven-dev.md` and `docs/process/dev-standards.md` — the workflow and coding standards.

## 3. Workflow
- Every change starts from a spec in `specs/<NN-name>/`, following `docs/process/spec-driven-dev.md` and its approval gates.

| Spec | Scenario | Mode |
|---|---|---|
| `01-core-shortener` | Link + redirect modules, auth, URL policy, audit | Greenfield (Mode A) |
| `02-redis-cache` | Redis cache as a decorator on the redirect path, plus link disable/takedown | Brownfield (Mode B enhancement) |
| `03-click-analytics` | Click analytics with privacy questions unresolved | Ambiguous (Mode A, `[NEEDS CLARIFICATION]`-heavy) |

- Spec 01 uses all three gates. Specs 02 and 03 may combine the plan and tasks gates into one approval.
- **Project rule for `plan.md` (low-level design).** Every plan includes, where relevant:
  - API contract changes as an OpenAPI excerpt
  - Schema changes as Flyway SQL plus a Mermaid `erDiagram` delta
  - A Mermaid `sequenceDiagram` for each new or changed flow
  - Ports and adapters added or touched, and which module owns them
  - Failure modes and how each is handled
  - A line confirming conformance with `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`
- Work one task from `tasks.md` at a time. Do not start the next task without the engineer's go-ahead.
- Out-of-scope ideas go under Follow-ups in the spec. Never implement them.

## 4. Agent roles
- **Builder** (default: Claude Code): implements the approved task, runs the gate, drafts the AI log entry.
- **Reviewer** (default: Codex, read-only): reviews the latest commit against the spec, this file, `ARCHITECTURE.md` and `SECURITY.md`. Findings ordered by severity: correctness/security, design, style. Never edits code.
- Roles may swap. The AI log records which agent did what.

## 5. Stack and commands
Java 21, Spring Boot 4.x, Gradle (Kotlin DSL), PostgreSQL 16, Redis 7 (from spec 02), Flyway, Docker Compose.
Testing: JUnit 5, AssertJ, Mockito (boundaries only), Testcontainers, ArchUnit. Quality: Spotless, Checkstyle, SpotBugs, JaCoCo. Security: OWASP Dependency-Check, gitleaks. API compatibility: oasdiff.

| Purpose | Command |
|---|---|
| Run everything locally | `docker compose up --build` |
| Unit tests only (fast) | `./gradlew test` |
| Full gate (definition of done) | `./gradlew check` (unit + integration + ArchUnit + lint + coverage + API compatibility) |
| Dependency scan | `./gradlew dependencyCheckAnalyze` (when dependencies change) |
| Secret scan | `gitleaks detect --source .` |
| Format | `./gradlew spotlessApply` |

Integration tests need Docker. If a command cannot run in your environment, say so and give the exact command.

## 6. Architecture rules (modular monolith, ports and adapters)
```
io.github.harshmittal.urlshortener
├── shared/       kernel: security (API-key auth), audit port + adapter, request IDs, error model, Clock
├── link/         module: create, delete, disable links; owns schema `link`
│   ├── api/          public interfaces other modules may call (e.g. LinkLookup)
│   ├── domain/       model, ports, services: plain Java, no Spring, no JPA, no Redis
│   └── adapter/      in/web (controllers, DTOs), out/persistence
├── redirect/     module: GET /{code} → 302; publishes click events
│   ├── domain/
│   └── adapter/      in/web, out/cache (spec 02), out/events
├── analytics/    module (spec 03): consumes click events; owns schema `analytics`
└── app/          composition root: Spring Boot main + the ONLY place adapters are wired to ports
```
- A module may use another module only through its `api` package or its published events. Never its `domain`, `adapter` or tables.
- Inside a module, dependencies point inward: `adapter` → `domain`. Never the reverse.
- Modules communicate in-process: synchronous calls through `api` interfaces, asynchronous click events through Spring application events behind an `EventPublisher` port. No message broker in the monolith.
- Each module owns its Postgres schema. No cross-schema queries or joins.
- Time comes from an injected `java.time.Clock`. Randomness comes from `ShortCodeGenerator`. Never call `Instant.now()` or `new Random()` in domain code.
- New infrastructure means a new adapter behind a port, plus a contract test for that port. Cross-cutting behaviour (caching, metrics) is a decorator over a port, never an edit to a service.
- ArchUnit tests in `src/test/java/.../architecture/` enforce all of the above. Never weaken or delete them.
- Design for extraction: anything that would block moving `redirect` into its own service (see ARCHITECTURE.md §10) is a design defect.

## 7. API conventions (versionless)
- Management API under `/api/**` with no version segment. Public redirect at `GET /{code}`.
- **Evolution rules.** Changes are additive only. Never remove or rename a field, change a type, or make an optional field required. Readers ignore unknown fields.
- The committed `api/openapi.yaml` is the contract baseline. `oasdiff` in `./gradlew check` fails the build on any breaking change.
- Retiring anything: add `Deprecation` and `Sunset` response headers first, then propose removal as a decision. A change that cannot be additive becomes a new resource name, never a version.
- Redirects use `302 Found`, never `301`.
- Errors use RFC 9457 `application/problem+json` (Spring `ProblemDetail`) with stable error codes. No stack traces or internal messages in responses.
- Resources owned by another API key return `404`, not `403`.
- DTOs are records validated with Bean Validation. Never expose JPA entities.

## 8. Persistence rules
- Every schema change is a new Flyway migration `V<n>__<description>.sql`. Never edit an applied migration.
- Short-code uniqueness is enforced by a DB unique constraint, never by check-then-insert.
- Parameterized queries only. No string-built SQL.
- A state-changing use case and its audit event commit in the same transaction.

## 9. Security rules (summary — detail in `docs/SECURITY.md`)
- Every target URL goes through the `UrlPolicy` port. The rules live in SECURITY.md §4 only.
- Short codes: `SecureRandom`, Base62, length 7. Collision → retry up to 3 times, then fail.
- API keys: stored only as SHA-256 hashes, compared in constant time, never logged or returned after creation.
- Secrets come from environment variables. Only `.env.example` with placeholders is committed.
- Rate-limit link creation per API key and redirects per client IP.
- Tests name the control they cover (`S-xx`); specs reference control IDs in their non-functional requirements.

## 10. Audit and logging
- Every state change and every security-relevant rejection writes an `audit.audit_events` row (SECURITY.md §6).
- `audit_events` is append-only. The app DB role has `INSERT` and `SELECT` only. Never add update or delete paths.
- Logs are structured JSON with `requestId` in MDC (from a valid `X-Request-Id`, otherwise generated).
- Never log API keys, full target URLs, raw client IPs or request bodies. Log the short code and key ID.

## 11. Testing rules
- Test first, per `docs/process/dev-standards.md`. Each test maps to an acceptance criterion: `@DisplayName("AC3: ...")`, or a control: `@DisplayName("S-01: ...")`.
- Domain unit tests use in-memory port fakes, never mocks of the class under test.
- Each port has one abstract contract test. Every adapter of that port extends it.
- Integration tests use Testcontainers (real Postgres, real Redis). No H2.
- Every security control has negative tests.
- Coverage gate: 80% line coverage on `domain` packages.

## 12. Definition of done (per task)
- [ ] Linked acceptance criteria pass, with test names that reference them
- [ ] `./gradlew check` passes; real output reported, never assumed
- [ ] No new SpotBugs or Checkstyle findings; gitleaks clean
- [ ] Spec, `api/openapi.yaml`, ARCHITECTURE.md and SECURITY.md updated if behaviour, design or controls changed
- [ ] `docs/AI_LOG.md` entry drafted (§13)
- [ ] One atomic commit: `type(scope): summary`; body says why and lists ACs

## 13. AI traceability and human sign-off
- After each task, draft a row in `docs/AI_LOG.md`: task, agent, role, prompt summary, files, what was generated, anything you were unsure about.
- The engineer fills in the outcome (Accepted / Edited / Rejected) and the rationale. Agents never write outcomes, approvals or sign-offs.
- **High-impact changes need an approved plan before any code:** Flyway migrations, security configuration and filters, `UrlPolicy`, authentication, audit, rate limiting, module boundaries, and anything in `app/`.

## 14. Never
- Never edit `AGENTS.md`, `CLAUDE.md`, `docs/DECISIONS.md`, `docs/SECURITY.md` or `docs/process/*` unless the engineer asks.
- Never disable, skip or weaken a test, ArchUnit rule, lint rule, compatibility check or security check to make a build pass.
- Never add a dependency without stating why in the plan; run the dependency scan after.
- Never commit secrets, real credentials or `.env`. Never read `.env`.
- Never add features, endpoints or config that are not in an approved spec.
