# Final engineering summary

## 1. In short

I built a URL shortener as a modular monolith in Java 21 and Spring Boot 4, with AI agents doing most of the typing and me doing the deciding. The goal was to show that AI can move fast without the engineer losing control of design, security or quality.

- **What was delivered:** three specs on one codebase. Spec 01 (from scratch): access keys, links, redirects, URL safety rules, audit trail. Spec 02 (change to existing code): a daily link quota per key. Spec 03 (vague request): "Marketing teams want to see their links", clarified first, then built as a list endpoint.
- **How it was controlled:** written agent rules, approval gates on every spec, test-first tasks with mutation checks, a read-only second agent for review, and a log of every AI step with my outcome.
- **Where the evidence is:** `docs/AI_LOG.md` for every AI step, `docs/DECISIONS.md` for every choice, `docs/SECURITY.md` for every threat and the test that covers it.

## 2. Plan and rationale

**Approach.** Decide first, then build in small approved steps.

1. **Planning session (before the build).** I explored options with AI and made decisions D1 to D12. I overrode several AI suggestions, for example `/api/v1` versioning.
2. **Project rules.** `AGENTS.md` sets architecture, testing, security and traceability rules every agent loads.
3. **Spec-driven delivery.** Each change goes spec, then plan, then tasks, each approved by me. Spec 01 used three separate gates; specs 02 and 03 combined plan and tasks into one (D16). Then one task at a time, test first, with `./gradlew check` green and my diff review before each commit.
4. **Review.** A second agent (Codex, read-only) reviews against the spec and threat model; I decide what to do with each finding.
5. **Scope control.** When time got tight I cut scope through written decisions (D13, D15, D16), never by quietly dropping a control.

**Key decisions** (full context in `docs/DECISIONS.md`):

| Decision | Why |
|---|---|
| Secure, auditable, composable baseline from v1 (D1) | Adding audit and auth later is expensive and error-prone |
| Java 21 and Spring Boot 4 (D2) | Boot 3 left open-source support in June 2026; a security-focused project shouldn't start on an unsupported framework |
| Postgres as the source of truth (D3) | Unique constraints make code collisions race-free; audit commits with the change |
| Hashed API keys; OAuth and KMS deferred behind ports (D4) | Callers are API clients; OAuth adds hours and hurts local runs without addressing a listed threat |
| `302`, not `301` (D5) | Browsers cache `301` forever, which would break takedowns and click counting later |
| Versionless API with an enforced compatibility check (D6) | Prevents breaking changes instead of making them feel safe behind `/v2` |
| Modular monolith with an extraction path (D7) | Service-style boundaries without the network, contract and container cost inside the time budget |
| Three scenarios on one evolving codebase (D9) | Changing code that was really built earlier is a more honest test |
| Builder and reviewer agents, engineer approves (D10) | Independent review without heavy orchestration |
| `JdbcClient` instead of JPA (D14) | `ON CONFLICT`, conditional updates and column grants stay visible in review |
| Scope trims (D13, D15) | Cut tooling overhead and polish, never a security control |
| Reshaped specs 02 and 03, creation limit 60 to 30 per minute (D16) | A daily quota and a list endpoint fit the time left better than a cache and analytics; nothing capped a key's total use before |

## 3. Artifacts

| Artifact | Purpose |
|---|---|
| `README.md` | What it is, how to run and try it, where everything is |
| `AGENTS.md`, `CLAUDE.md` | Rules every agent follows |
| `docs/ARCHITECTURE.md` | Design with diagrams, including the target state and extraction path |
| `docs/DECISIONS.md` | Decisions D1 to D16 |
| `docs/SECURITY.md` | Threats T1 to T18, controls S-01 to S-21, each with its test |
| `docs/AI_LOG.md` | Gate approvals, task log, reviews, rejections, work sessions |
| `docs/process/` | My general spec-driven workflow and coding standards, reused unchanged |
| `specs/01-core-shortener/` | Spec, detailed plan and tasks for the core |
| `specs/02-daily-link-quota/` | How the code behaved before the change, then spec, plan and tasks for the quota |
| `specs/03-list-links/` | The 11 questions raised and answered, then spec, plan and tasks |
| `api/openapi.yaml` | The committed API contract; the build fails on breaking changes against it |

## 4. Validation

**Every commit:**

- `./gradlew check`: unit tests, integration tests on a real Postgres (Testcontainers), ArchUnit boundary rules, Spotless, JaCoCo (at least 80% line coverage on domain code) and the API compatibility check.
- gitleaks secret scan.
- My review of the full diff.

**Every spec:** an end-to-end smoke test against the full Docker stack (`scripts/smoke-test.sh`), including a database outage check, run again on `main` after each merge.

**Proving the tests actually catch things.** Each task included mutation checks: the agent deliberately broke a control, showed the tests fail, then restored the file exactly (SHA-256 match). Examples:

| Mutation | Result |
|---|---|
| Removed `ON CONFLICT` on short-code insert | Collision test failed |
| Moved the audit write outside the transaction | Atomicity test failed |
| Removed default-deny on `/api` | Authorization test failed |
| Removed the `10.0.0.0/8` block | 7 URL policy tests failed |
| Removed decimal and hex IP parsing | 16 tests failed, including `http://2130706433` (127.0.0.1) |
| Stopped counting deleted links toward the daily quota (spec 02) | Quota tests failed |
| Removed the owner filter from the list query (spec 03) | Owner-scoping tests failed |

**Numbers**

| Measure | Final |
|---|---|
| Tests | 573 (363 unit, 210 integration), all passing |
| Domain line coverage | 95.5% (gate: 80%) |
| gitleaks | Clean |
| Dependency scan (CVSS 7 or above blocks release) | TODO: result of `./gradlew dependencyCheckAnalyze` |
| Smoke test, including database outage `503` | All checks pass on `main` with all three specs |

**Agent reviews**

| Review | Findings | What I did |
|---|---|---|
| Spec 01, after T1 and T2 | 12 | 1 code fix (default-deny on `/api`), 5 doc fixes, 4 already planned, 1 deferred, 1 rejected as already tracked |
| Spec 01, after T3 | None | Accepted |
| Spec 01, after T6 | TODO: confirm. Planned in D15 but no row in the AI log | |
| Spec 02 | None | Accepted |
| Spec 03 | None | Accepted (run after the merge, so it confirmed rather than gated it) |

## 5. Security highlights

Full list with tests in `docs/SECURITY.md`.

- **API keys:** 256-bit random, only a SHA-256 hash stored, constant-time comparison, and a dummy comparison on unknown keys so response time doesn't reveal whether a key exists. The admin key is configured only as its hash.
- **Authorization:** owner-scoped everywhere, including the list query itself. Another owner's link returns the same `404` as an unknown code. Every `/api` route needs an explicit role; anything unlisted is denied.
- **URL policy:** `http` and `https` only. Private, loopback, link-local, CGNAT, multicast and other special ranges blocked, including IPv4 hidden inside IPv6 (mapped, NAT64, 6to4). IP literals parsed by hand the way browsers parse them (decimal, octal, hex, short forms); hostnames are never looked up in DNS because the service never fetches targets. Userinfo, links to itself, control characters and URLs over 2,048 characters rejected. International hosts stored as punycode.
- **Abuse limits:** 30 link creations per key per minute and 500 per key per UTC day, both answering `429` with `Retry-After` and both audited. Request bodies over 8 KB are refused before authentication.
- **Audit:** append-only table enforced by database grants (tested), written in the same transaction as the change it records. Two database users: one for migrations, a least-privilege one for the app.
- **Runtime:** container runs as a non-root user on a read-only file system; Swagger UI only in the local profile; structured logs with request IDs.
- **Hygiene:** no keys, full URLs, raw IPs or bodies in logs; safe error responses with no stack traces.

## 6. Risks and trade-offs

| Trade-off | What I gained | What I gave up | Mitigation |
|---|---|---|---|
| Monolith instead of services | Speed, one deployable, simple local run | Independent scaling and deploys | Module boundaries enforced by ArchUnit; documented extraction path |
| API keys instead of OAuth | Runs locally with no identity provider | No user login, SSO or delegated access | `Authenticator` port makes OAuth an adapter, not a rewrite |
| In-memory rate limiter | No extra infrastructure | Limits are per instance | Redis-backed Bucket4j when scaled out |
| Daily quota counted from the links table | No new table or counter to keep in sync | Two creates at the same instant can both pass at the limit | Acceptable overshoot of a few links; a locked counter row is a follow-up |
| No DNS lookups in URL policy | No SSRF surface, fast, deterministic | Hostnames pointing at internal IPs are accepted | The service never fetches targets; revisit if link previews are added |
| `302` redirects | Takedowns and analytics can work later | Every click hits the service | Redirect cache is a follow-up (D16) |
| Versionless API | No parallel versions to maintain | Discipline must be enforced | oasdiff fails the build on breaking changes |
| Compatibility check against a committed file (D15) | Simple build | A breaking change committed together with an edited `openapi.yaml` isn't caught | I review contract diffs; a stricter check is a follow-up |
| One team = one API key (spec 03) | No new team model | Rotating a key hides the old key's links from the list | Teams grouping several keys is a follow-up |
| List capped at 100, no paging yet (spec 03) | Small, simple first version | Owners with more than 100 links can't see them all | `{ items }` envelope lets cursor paging be added without breaking clients |

**Risks to the AI-assisted process itself**

- **Agents not following the rules.** Seen in practice: code before tests in T2, and file edits through shell scripts. Both were caught, logged and turned into enforced rules (red-before-green from T3; a rule in `AGENTS.md` banning script edits).
- **Secrets exposed to agents.** Deny rules block `.env` reads, but approved shell commands aren't covered: a gitleaks run over the working folder opened `.env` (findings were redacted). Mitigated by scanning tracked files only, keeping no real secrets in the project folder, and reviewing every shell command before approving it.
- **Tests that pass for the wrong reason.** Addressed with mutation checks; where a test passed before implementation, it was kept and labelled as a characterization test rather than claimed as red-to-green.
- **Reviewer agreement as false comfort.** The later reviews found nothing. That is useful but not proof; my own diff review and the mutation checks carry most of the weight.
- **My own mistakes in prompts.** A find-and-replace prompt of mine didn't match the file, so a docs edit silently didn't apply. The agent flagged it later; lesson logged.

## 7. Assumptions

- Callers are API clients, not people logging in through a browser.
- One instance in this prototype; TLS and a firewall sit in front of it.
- The service never fetches target URLs, so DNS-based SSRF doesn't apply.
- The first admin key is generated by a script and configured only as its hash.
- Key revocation is a manual database step until a revoke endpoint exists.
- Client IP comes from the network connection, not `X-Forwarded-For`, since trusting that header by default lets callers choose their own IP.
- **Spec 02:** the quota day is the UTC day; deleted links still count, so deleting doesn't free up quota.
- **Spec 03:** a "team" is one API key; "see their links" means active links only, newest first, with no filters or totals; reading the list is not audited because nothing changes.

All spec-level assumptions are listed in each `spec.md`, and spec 03's in its clarification log.

## 8. Known limitations

- Rate limits are per instance (in memory).
- Behind Docker networking every client may look like the same IP, which weakens IP hashing in audit until trusted-proxy configuration is added.
- With no per-IP redirect limit (deferred in D16), failed-auth audit rows can be inflated by an unauthenticated caller, and guessing short codes is limited only by the code space (62⁷, about 3.5 trillion).
- If an audit write fails on a rejection, the only evidence is a WARN log line.
- Log levels follow the convention but aren't test-enforced.
- There is no takedown: a harmful link that passes the URL policy can only be removed by its owner deleting it.
- The daily quota can overshoot slightly under truly simultaneous requests.
- The list shows at most 100 links and hides links made with an older, rotated key.
- Not blocked yet: documentation IP ranges, `240.0.0.0/4`, NAT64 local-use `64:ff9b:1::/48`.

## 9. Deferred items and next steps

| Item | Why deferred | Path |
|---|---|---|
| Redis redirect cache | Cut in D16 to fit the time | `LinkLookup` decorator, fail-open (D3) |
| Link takedown by admin | Cut in D16 | Additive endpoint plus cache eviction |
| Per-IP redirect rate limit | Cut in D16 | Bucket4j on the redirect path |
| Click analytics | Cut in D16 | Click events behind the `EventPublisher` port, analytics module (D11) |
| Cursor paging for the list | Not needed at 100 links | `cursor` parameter and `nextCursor` in the existing envelope |
| Teams grouping several keys | Not needed yet | Team model, list across a team's keys |
| Quota headers or endpoint | Not needed yet | `RateLimit` headers or `GET /api/quota` |
| Strict quota under concurrency | Overshoot is small | Locked per-key counter row |
| Checkstyle, SpotBugs | Tuning cost; SpotBugs lags new Java and Boot | Add to `./gradlew check` |
| Dependency scan on every build | Slow first run, needs an API key | Move from pre-release check into CI |
| Audit copy to the log stream | Plumbing, not a control; the table is the source of truth | `MirroringAuditSink` decorator, after commit |
| Stricter API drift check | Git baseline plumbing | Compare against the previous release tag |
| Key revocation endpoint | Not needed for the core flow | Additive endpoint |
| Trusted-proxy client IP | Needs deployment knowledge | Configure known proxies |
| OAuth / OIDC, KMS | Not justified by the threat model | Adapters behind existing ports |
| Metrics and tracing | No services yet | Add at extraction |
| Malicious-destination detection | Needs a reputation feed | Adapter behind `UrlPolicy` |

## 10. Time

TODO: total from the work sessions table in `docs/AI_LOG.md`.

Where the time went, roughly: planning and decisions (about 2 hours, before the build), environment setup and project rules (about 1 hour, including about 30 minutes lost to Docker on Apple silicon and folder permissions), spec 01 (most of the build), spec 02 (about 45 minutes of scoping plus 55 minutes to build and merge), spec 03 (TODO), and wrap-up.
