# Security and Audit Baseline

Owner: the engineer. Agents may propose changes; only the engineer edits this file.

## 1. Scope
Applies to the URL shortener (a modular monolith: link, redirect and analytics modules), its PostgreSQL and Redis dependencies, its container image, and the AI-assisted way it is built (§8).
Security is proportionate to the threat model below. Controls not justified by a listed threat are recorded as out of scope (§10), not silently omitted.

## 2. Assets
| Asset | Why it matters |
|---|---|
| Link mappings (code → target URL) | Tampering redirects users to attacker sites |
| API keys | Grant create/delete rights over an owner's links |
| Audit trail | Evidence of who did what; must be trustworthy |
| Click data | May contain personal data (IP, user agent) |
| Service availability | Broken redirects break every published link |
| Source code and secrets in the build environment | Exposed to AI agents during development |

## 3. Threat model
| ID | Threat | Impact | Control(s) | Status |
|---|---|---|---|---|
| T1 | Shortening a malicious scheme (`javascript:`, `data:`, `file:`) | Script execution / phishing via a trusted domain | S-01 | Implemented (`StandardUrlPolicyTest`, `CreateLinkIT`) |
| T2 | Redirect to internal or loopback hosts | Users' browsers pivoted into internal networks | S-02 | Implemented (`StandardUrlPolicyTest`, `UrlRejectedIT`) |
| T3 | Phishing disguised with userinfo (`https://bank.com@evil.com`) | Users misled about destination | S-03 | Implemented (`StandardUrlPolicyTest`, `UrlRejectedIT`) |
| T4 | Redirect loops via own domain | Resource exhaustion, broken links | S-04 | Implemented (`StandardUrlPolicyTest`, `UrlRejectedIT`) |
| T5 | Header injection through crafted target URL | Response splitting | S-05 | Implemented (`StandardUrlPolicyTest`, `UrlRejectedIT`) |
| T6 | Enumerating short codes | Discovery of private links | S-06 | Implemented (`ShortCodeGeneratorContract`, `CodeCollisionIT`); per-IP redirect limit is a follow-up (D16) |
| T7 | Unauthorized delete or modify of another owner's link | Integrity loss | S-07, S-08 | Implemented (`AuthenticationIT`, `ManageLinkIT`) |
| T8 | API key theft from DB, logs or responses | Account takeover | S-07, S-12 | S-07 implemented (`KeyIssuanceIT`, `ApiKeyAuthenticatorTest`); S-12 implemented (`LogHygieneIT`) |
| T9 | Abuse at volume (spam creation, redirect flooding) | Cost, reputation, availability | S-09 | Per-minute creation limit implemented (`CreationRateLimitIT`); daily link quota implemented (`DailyQuotaIT`, `LinkServiceTest`, `JdbcLinkRepositoryIT`); per-IP is a follow-up (D16) |
| T10 | Tampering with or deleting audit records | Loss of accountability | S-10, S-11 | Implemented (`AuditAtomicityIT`, `AuditEventShapeIT`, `DatabasePrivilegesIT`) |
| T11 | Sensitive data in logs (keys, tokens in URLs, raw IPs) | Data leak | S-12 | Implemented (`LogHygieneIT`) |
| T12 | Vulnerable dependencies or leaked secrets in repo | Compromise | S-13, S-14 | Planned (spec 01) |
| T13 | Container breakout / excess privileges | Host compromise | S-15 | Implemented (`scripts/smoke-test.sh`) |
| T14 | Information leakage in errors / actuator | Recon for attackers | S-16, S-17 | S-16 implemented (`ErrorResponsesIT`, `SharedExceptionHandlerTest`; real DB outage checked by the T6 smoke test, D15); S-17 implemented (`ActuatorExposureIT`) |
| T15 | Personal data in click analytics | Privacy and compliance exposure | S-18 | Follow-up with click analytics (D16) |
| T16 | Stale cache serving deleted or disabled links | Takedown ineffective | S-19 | Follow-up with the Redis cache and takedown (D16) |
| T17 | Module boundary bypass (one module reading another's tables) | Hidden coupling; controls bypassed at extraction | S-20 | Implemented (`ArchitectureTest`, `SchemaIT`) |
| T18 | Unsafe AI-assisted development (secrets exposed to agents, unreviewed generated code, insecure suggestions) | Leaked secrets; vulnerable code shipped | S-21 | Active from kickoff |

Update Status to `Implemented (<test name>)` as controls land.

## 4. Controls — input and URL policy (`UrlPolicy` port, link module)
- **S-01 Scheme allowlist.** Accept only `http` and `https`, compared case-insensitively after trimming. Reject everything else.
- **S-02 Host restrictions.** Reject `localhost` and its variants (`*.localhost`, `localhost.localdomain`), and literal IPs in blocked ranges. Normalize alternate IP encodings (decimal, octal, hex, short forms, as browsers parse them) before checking. Hostnames are not DNS-resolved: the service never fetches targets, so DNS-based SSRF does not apply (documented trade-off).
  - IPv4: unspecified `0.0.0.0/8`, private `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, CGNAT `100.64.0.0/10`, loopback `127.0.0.0/8`, link-local `169.254.0.0/16`, benchmarking `198.18.0.0/15`, multicast `224.0.0.0/4`, broadcast `255.255.255.255`.
  - IPv6: unspecified `::`, loopback `::1`, unique-local `fc00::/7`, link-local `fe80::/10`, site-local `fec0::/10`, multicast `ff00::/8`.
  - IPv6 that carries an IPv4 address is checked against the IPv4 ranges: IPv4-mapped `::ffff:0:0/96`, IPv4-compatible `::/96`, NAT64 `64:ff9b::/96` and 6to4 `2002::/16`.
- **S-03 No userinfo.** Reject URLs containing `user@` or `user:pass@`.
- **S-04 No self-reference.** Reject targets whose host is the service's own public host.
- **S-05 Well-formed only.** Parse with `java.net.URI`; reject control characters, whitespace, CR/LF, and URLs over 2,048 characters. Store the normalized form; internationalized hosts as punycode.

## 5. Controls — identity, access and abuse
- **S-06 Unguessable codes.** `SecureRandom` Base62, length 7 (~3.5 × 10¹²). Uniqueness via DB constraint with up to 3 retries. If custom aliases are added later, reserved words (`api`, `actuator`, `health`, `docs`, …) are blocked.
- **S-07 API key handling.** 256 bits of randomness with a public key-ID prefix for lookup. Only a SHA-256 hash is stored (safe for high-entropy keys, unlike human passwords). Constant-time comparison. Shown once at creation, never again. Revocable.
- **S-08 Owner scoping and default-deny authorization.** Management endpoints act only on links owned by the calling key. Cross-owner access returns `404`. Every `/api` route needs an explicit role; anything unlisted is denied, even with a valid key.
- **S-09 Rate limiting.** Per API key for link creation: a per-minute limit, default 30 (spec 01, D16), and a daily link quota, default 500 per day (spec 02, D16). Per client IP for redirects and unauthenticated `/api/**` requests is a follow-up (D16); it needs trusted-proxy configuration to see real client IPs behind Docker or a load balancer. The per-minute limit uses Bucket4j; the daily quota is counted from stored links, so it holds across restarts and instances. Exceeding either returns `429` (`rate-limited` or `quota-exceeded`) with `Retry-After` and writes a `RATE_LIMITED` audit event (`CREATE_LIMIT` or `DAILY_QUOTA`). Limitation: in-memory per-minute buckets are per instance (§10); the daily quota may overshoot by one or two under concurrent requests (spec 02 L1).

## 6. Controls — audit (shared kernel)
- **S-10 Audit events.** One row per event in `audit.audit_events`:
  `id, occurred_at, request_id, actor_key_id (nullable), action, resource_type, resource_id, outcome, reason_code, client_ip_hash`.
  Events: `LINK_CREATED`, `LINK_DELETED`, `LINK_DISABLED`, `API_KEY_CREATED`, `API_KEY_REVOKED`, `AUTH_FAILED`, `ACCESS_DENIED`, `RATE_LIMITED`, `URL_REJECTED`.
  Successful redirects are not audited (volume); they become click events instead.
  State changes write their audit row in the same transaction.
- **S-11 Append-only.** The application DB role has `INSERT` and `SELECT` only on `audit.audit_events`, granted in a Flyway migration and verified by an integration test that attempts `UPDATE` and `DELETE`. Stretch: hash-chain rows for tamper evidence.

## 7. Controls — data handling, supply chain, runtime and architecture
- **S-12 Log hygiene.** Structured JSON with `requestId`. Never log API keys, full target URLs, raw client IPs or request bodies. Log short code, key ID and a salted IP hash.
- **S-13 Dependency scanning.** OWASP Dependency-Check runs as a manual pre-release gate (D13) and blocks submission on CVSS ≥ 7 unless a suppression records a justification and an expiry date.
- **S-14 Secret scanning.** gitleaks before each commit and before submission. Secrets only from environment variables; only `.env.example` is committed, with placeholders; `compose.yaml` holds no credentials and reads every secret from `.env`.
- **S-15 Container hardening.** Minimal JRE base image, non-root user, read-only root filesystem where possible. Implemented (`scripts/smoke-test.sh`): distroless Java 21 `nonroot`, read-only root filesystem with a tmpfs `/tmp`, all capabilities dropped, `no-new-privileges`.
- **S-16 Safe errors.** RFC 9457 problem details with stable error codes; no stack traces, SQL or class names in responses.
- **S-17 Actuator exposure.** Only `health` and `info`, on a separate management port not exposed publicly. A `prometheus` endpoint is a follow-up.
- **S-18 Analytics privacy (follow-up, D16).** No raw IPs stored. Retention, bot handling and stats visibility are decided in the click-analytics spec.
- **S-19 Cache consistency (follow-up, D16).** Delete and disable evict the cache entry; TTL as backstop. Redis unavailable → read the database (fail open on cache, never on auth or policy).
- **S-20 Module isolation.** Each module owns its Postgres schema; ArchUnit forbids access to another module's `domain`, `adapter` or persistence classes. In the monolith one DB role serves all modules; per-module DB roles arrive when a module is extracted (§10).

Security response headers on all API responses: `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`. HSTS is set at the TLS terminator.

## 8. Controls — secure AI-assisted development
- **S-21 AI usage guardrails.**
  - Agents are denied read access to `.env` files and secrets (`.claude/settings.json` deny rules; reviewer agent runs read-only).
  - Requirements are given to agents in the engineer's own words; internal source documents are not pasted into external tools.
  - No generated code is committed without engineer review of the diff; high-impact areas (AGENTS.md §13) need an approved plan first.
  - A second agent reviews at milestones against this file: after T3, after T6, and after each later spec (D15); findings and decisions are recorded in `docs/AI_LOG.md`.
  - Generated code passes the same gates as human code: tests, ArchUnit, static analysis, dependency and secret scans.
  - Residual risk: path deny rules do not cover shell commands the engineer approves. Mitigations: no real secrets in the project folder, every shell command reviewed before approval, reviewer agent read-only by instruction (Codex in IDE; a sandboxed CLI is the stronger option).
  - Observed in T6: a gitleaks scan of the working folder, run as an approved shell command, opened .env (findings redacted). Mitigation: scan only tracked files and src; never scan the working folder.

## 9. Verification
| Gate | How | When |
|---|---|---|
| Negative security tests per control | JUnit, named `S-xx: ...` | Every `./gradlew check` |
| Append-only audit | Testcontainers integration test | Every `./gradlew check` |
| Module isolation and layering | ArchUnit | Every `./gradlew check` |
| API compatibility | oasdiff against `api/openapi.yaml` | Every `./gradlew check` |
| Static analysis | Checkstyle, SpotBugs | Deferred (D13) |
| Dependencies | OWASP Dependency-Check | Manual pre-release gate, before submission (D13) |
| Secrets | gitleaks | Before each commit, and before submission |
| Human review | Engineer review of every diff; second-agent review at milestones: after T3, after T6, and after each later spec (D15) | Every task (engineer); milestones (agent) |

## 10. Out of scope and residual risks
| Item | Reason | Path forward |
|---|---|---|
| OAuth 2.0 / OIDC | Callers are API clients; hashed scoped keys cover identity, authz and audit for this threat model. An IdP harms local runnability. | JWT resource-server adapter behind the `Authenticator` port |
| KMS / Vault | No encrypted secrets at rest; keys are hashed | `SecretsProvider` adapter |
| Service-to-service auth | All modules run in one process today | mTLS or signed service tokens when redirect is extracted (D7 stage C) |
| Per-module DB roles | One deployable, one connection pool | Separate roles per schema at extraction |
| TLS termination, WAF | Belong to the ingress layer | Reverse proxy / platform |
| Malicious-destination detection | Needs a third-party reputation feed | Adapter behind `UrlPolicy` |
| Distributed rate limiting | Single instance in prototype | Redis-backed Bucket4j |
| DNS rebinding / resolved-IP checks | Service never fetches targets | Revisit if link previews are added |

Residual risk accepted: a public phishing URL that passes policy can be shortened. Future mitigation, deferred to a follow-up by D16: takedown via `LINK_DISABLED`, which is audited and evicts the cache. Until then, a malicious link can only be removed by its owner deleting it.
