# Decision log

Owner: the engineer. Agents may propose new entries; only the engineer accepts them.
Format: lightweight ADRs. A changed decision gets a new entry that supersedes the old one; old entries are never edited.

**Pre-planning disclosure.** Before the build window, options and trade-offs for D1–D12 were explored in a planning session with Claude (chat). The AI presented alternatives; every decision below was made by the engineer.

| ID | Decision | Status |
|---|---|---|
| D1 | Secure, auditable, composable baseline from v1 | Accepted |
| D2 | Java 21, Spring Boot 4.x, Gradle | Accepted (amended) |
| D3 | PostgreSQL as source of truth; Redis as fail-open cache | Accepted |
| D4 | Hashed API keys; OAuth and KMS deferred behind ports | Accepted |
| D5 | 302 redirects | Accepted |
| D6 | Versionless API with enforced evolution rules | Accepted |
| D7 | Modular monolith with a staged extraction path | Accepted |
| D8 | Design documented at project level (HLD file + LLD in plans) | Accepted |
| D9 | Three scenarios mapped to one evolving codebase | Accepted |
| D10 | Builder and reviewer agents, engineer as approver | Accepted |
| D11 | In-process events inside the monolith; no broker | Accepted |
| D12 | Redis introduced in the brownfield scenario | Accepted |
| D13 | Scope cuts for the time budget | Accepted |
| D14 | Persistence with `JdbcClient`, not JPA | Accepted |

---

### D1 Secure, auditable, composable baseline from v1
- **Context:** Production-grade expectation; reviewers in a regulated industry weigh controls and audit heavily.
- **Decision:** Ports and adapters, URL policy, hashed API keys, append-only audit, structured logs with request IDs, and security scans in the build gate are part of v1, not later work.
- **Consequences:** v1 takes longer (about 2 hours). Mitigated by building one thin vertical slice first (create, redirect, audit). Every control traces to a threat in SECURITY.md.

### D2 Java 21, Spring Boot 4.x, Gradle
- **Context:** The engineer's standards target the JVM toolchain; Java is the common enterprise stack.
- **Decision:** Java 21 LTS, Spring Boot 4.x (latest stable at project creation), Gradle (Kotlin DSL); Flyway, Testcontainers, ArchUnit, springdoc, Bucket4j.
- **Alternatives:** Python/FastAPI (faster scaffolding, weaker fit); Maven (equally valid, Gradle chosen for task wiring).
- **Consequences:** More verbose code, absorbed by AI; mature security and architecture tooling.
- **Amendment (2026-10-06, at project creation):** originally planned as Spring Boot 3. Changed to 4.x because the 3.x line reached end of open-source support on 2026-06-30; starting a security-focused project on an unsupported framework is not defensible. Plugin compatibility with Boot 4 (springdoc, OWASP Dependency-Check, SpotBugs) is verified in spec 01's plan. Java 21 LTS chosen over the installed JDK 26 (non-LTS) for toolchain compatibility.

### D3 PostgreSQL as source of truth; Redis as fail-open cache
- **Decision:** Postgres for links, audit and analytics (schema per module). Redis caches redirect lookups behind a port.
- **Why:** A unique constraint makes collisions race-free; mutations and audit rows commit in one transaction; DB grants enforce append-only audit. Redis is shared across instances.
- **Consequences:** Cache failure falls back to the DB. Auth and URL policy never fail open.

### D4 Hashed API keys; OAuth and KMS deferred behind ports
- **Context:** Callers are API clients. Keys are high-entropy and hashed, so nothing needs encryption at rest.
- **Decision:** 256-bit random keys, SHA-256 hash stored, constant-time compare, revocable, owner-scoped.
- **Alternatives:** OAuth with Keycloak (1–2 hours, harms local runnability, no current threat addressed); KMS/Vault (no secrets to protect).
- **Consequences:** `Authenticator` and `SecretsProvider` ports make OAuth and KMS adapter additions, not rewrites. Revisit when human users, SSO or delegated access appear.

### D5 302 redirects
- **Decision:** `302 Found` for every redirect.
- **Why:** 301 is cached permanently by browsers, which would break click analytics, malicious-link takedowns and target changes.
- **Consequences:** Every click reaches the service; the redirect path needs caching (D12) and is the first extraction candidate (D7).

### D6 Versionless API with enforced evolution rules
- **Decision:** No version segment in URLs. One evolving contract governed by rules the build enforces.
- **Why:** No parallel versions to maintain, no client migrations, compatibility discipline checked on every build rather than delegated to a URL.
- **Guardrails:** additive-only changes; tolerant readers; `oasdiff` against the committed `api/openapi.yaml` fails the build on breaking changes; `Deprecation`/`Sunset` headers before retirement; a change that cannot be additive becomes a new resource name.
- **Alternative:** `/api/v1` path versioning (cheap insurance, but makes breaking changes feel safe instead of preventing them).

### D7 Modular monolith with a staged extraction path
- **Context:** Services were the initial preference; their real cost in a 4–5 hour budget is contracts, network failure handling, more containers and more review.
- **Decision:** Build a modular monolith (link, redirect, analytics modules; shared kernel). Target state: three services. Path: M → C (extract redirect) → A (extract analytics) → B (own data per service) only if triggered.
- **Triggers:** C when redirect load or latency needs diverge from link management; A when analytics needs independent scaling; B only for separate teams or shared-DB strain.
- **Consequences:** ArchUnit enforces module boundaries; schema per module; modules interact only via `api` interfaces and events, so extraction swaps an in-process adapter for a network adapter.

### D8 Design documented at project level
- **Decision:** HLD lives in `docs/ARCHITECTURE.md`. LLD lives in each spec's `plan.md`, which must include API, schema, sequence and failure-mode sections (AGENTS.md §3). Generic process skills stay unchanged.
- **Alternative:** Extending the generic skills (deferred: more reusable, but not needed within the time budget).

### D9 Three scenarios on one evolving codebase
- **Decision:** Greenfield builds spec 01 (core). Brownfield treats the tagged v1 as existing code for spec 02 (cache + takedown). Ambiguous scenario is spec 03 (click analytics with unresolved privacy and counting questions).
- **Why:** Brownfield work on real, previously built code is a more honest test than a synthetic codebase.

### D10 Builder and reviewer agents, engineer as approver
- **Decision:** One agent builds an approved task; a second agent reviews the commit read-only. The engineer approves every gate, decides on every finding, and records outcomes in `docs/AI_LOG.md`.
- **Consequences:** Independent review without extra orchestration; full traceability of generated, edited and rejected output.

### D11 In-process events inside the monolith
- **Decision:** Click events use Spring application events behind an `EventPublisher` port; no broker.
- **Consequences:** Redirects never block on analytics. At extraction, the port gets a broker adapter (e.g. Redis Streams) without touching domain code.

### D12 Redis introduced in the brownfield scenario
- **Decision:** Spec 01 reads links from Postgres only. Spec 02 adds Redis as a `LinkLookup` decorator.
- **Why:** Gives the brownfield scenario a real, cross-cutting change with impact analysis and failure handling.

### D13 Scope cuts for the time budget
- **Context:** About 3.5–4 hours of build time remain, split across sessions. Cuts target tooling overhead, never functionality or security controls.
- **Decision:**

| Cut | Why | What still covers it |
|---|---|---|
| OWASP Dependency-Check moved from every build to a pre-release gate | First run downloads a large vulnerability database and needs an API key; too slow for every build | Run once before submission; blocks release on CVSS ≥ 7 |
| Checkstyle deferred | Rule sets need tuning; catches style, not defects | Spotless enforces formatting |
| SpotBugs deferred | Tends to lag new Java/Boot versions; high debugging risk | Tests, ArchUnit, second-agent review, engineer diff review |
| Spec 01 delivered in 6–8 tasks, not 12+ | Each task carries commit, review and log overhead | Each task is still one reviewable change linked to ACs |
| Spec 03 kept narrow | The scenario assesses handling ambiguity, not feature breadth | Written clarifications plus a minimal click-count endpoint |

- **Unchanged:** all three scenarios, URL policy, API-key auth, owner scoping, append-only audit, tests, ArchUnit, coverage, oasdiff, gitleaks, spec gates, AI log.
- **Consequences:** Fewer static checks in the prototype. Deferred checks are listed as next steps in SUMMARY.md.

### D14 Persistence with `JdbcClient`, not JPA
- **Context:** Accepted at spec 01 Gate 2 (2026-10-07). The scaffold includes Spring Data JPA. Spec 01 has three small tables, relies on `INSERT ... ON CONFLICT DO NOTHING` for collision-safe code inserts (S-06), and uses column-level grants for least privilege (R21).
- **Decision:** Persistence adapters use Spring's `JdbcClient` with explicit, parameterized SQL. `spring-boot-starter-data-jpa` is replaced by `spring-boot-starter-jdbc`. Transactions go through a `UnitOfWork` port backed by `TransactionTemplate`.
- **Alternatives:** Spring Data JPA (less SQL to write, but hides `ON CONFLICT` and update statements behind entity state, and adds an entity layer that must be kept out of the domain).
- **Consequences:** SQL and transaction boundaries are visible in review. Row mapping is written by hand. AGENTS.md §7's "never expose JPA entities" still holds trivially. Later specs add persistence the same way unless a new decision supersedes this one.
