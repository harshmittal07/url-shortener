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
| T1 | Shortening a malicious scheme (`javascript:`, `data:`, `file:`) | Script execution / phishing via a trusted domain | S-01 | Planned (spec 01) |
| T2 | Redirect to internal or loopback hosts | Users' browsers pivoted into internal networks | S-02 | Planned (spec 01) |
| T3 | Phishing disguised with userinfo (`https://bank.com@evil.com`) | Users misled about destination | S-03 | Planned (spec 01) |
| T4 | Redirect loops via own domain | Resource exhaustion, broken links | S-04 | Planned (spec 01) |
| T5 | Header injection through crafted target URL | Response splitting | S-05 | Planned (spec 01) |
| T6 | Enumerating short codes | Discovery of private links | S-06 | Planned (spec 01) |
| T7 | Unauthorized delete or modify of another owner's link | Integrity loss | S-07, S-08 | Planned (spec 01) |
| T8 | API key theft from DB, logs or responses | Account takeover | S-07, S-12 | Planned (spec 01) |
| T9 | Abuse at volume (spam creation, redirect flooding) | Cost, reputation, availability | S-09 | Planned (spec 01) |
| T10 | Tampering with or deleting audit records | Loss of accountability | S-10, S-11 | Planned (spec 01) |
| T11 | Sensitive data in logs (keys, tokens in URLs, raw IPs) | Data leak | S-12 | Planned (spec 01) |
| T12 | Vulnerable dependencies or leaked secrets in repo | Compromise | S-13, S-14 | Planned (spec 01) |
| T13 | Container breakout / excess privileges | Host compromise | S-15 | Planned (spec 01) |
| T14 | Information leakage in errors / actuator | Recon for attackers | S-16, S-17 | Planned (spec 01) |
| T15 | Personal data in click analytics | Privacy and compliance exposure | S-18 | Spec 03 |
| T16 | Stale cache serving deleted or disabled links | Takedown ineffective | S-19 | Spec 02 |
| T17 | Module boundary bypass (one module reading another's tables) | Hidden coupling; controls bypassed at extraction | S-20 | Planned (spec 01) |
| T18 | Unsafe AI-assisted development (secrets exposed to agents, unreviewed generated code, insecure suggestions) | Leaked secrets; vulnerable code shipped | S-21 | Active from kickoff |

Update Status to `Implemented (<test name>)` as controls land.

## 4. Controls — input and URL policy (`UrlPolicy` port, link module)
- **S-01 Scheme allowlist.** Accept only `http` and `https`, compared case-insensitively after trimming. Reject everything else.
- **S-02 Host restrictions.** Reject `localhost` and its variants, and literal IPs in loopback, private (RFC 1918), link-local, CGNAT, unique-local IPv6 and unspecified ranges. Normalize alternate IP encodings (decimal, octal, hex, short forms) before checking. Hostnames are not DNS-resolved: the service never fetches targets, so DNS-based SSRF does not apply (documented trade-off).
- **S-03 No userinfo.** Reject URLs containing `user@` or `user:pass@`.
- **S-04 No self-reference.** Reject targets whose host is the service's own public host.
- **S-05 Well-formed only.** Parse with `java.net.URI`; reject control characters, whitespace, CR/LF, and URLs over 2,048 characters. Store the normalized form; internationalized hosts as punycode.

## 5. Controls — identity, access and abuse
- **S-06 Unguessable codes.** `SecureRandom` Base62, length 7 (~3.5 × 10¹²). Uniqueness via DB constraint with up to 3 retries. If custom aliases are added later, reserved words (`api`, `actuator`, `health`, `docs`, …) are blocked.
- **S-07 API key handling.** 256 bits of randomness with a public key-ID prefix for lookup. Only a SHA-256 hash is stored (safe for high-entropy keys, unlike human passwords). Constant-time comparison. Shown once at creation, never again. Revocable.
- **S-08 Owner scoping.** Management endpoints act only on links owned by the calling key. Cross-owner access returns `404`.
- **S-09 Rate limiting.** Per API key for link creation; per client IP for redirects (Bucket4j). Exceeding returns `429` with `Retry-After` and writes an audit event. Limitation: in-memory buckets are per instance (§10).

## 6. Controls — audit (shared kernel)
- **S-10 Audit events.** One row per event in `audit.audit_events`:
  `id, occurred_at, request_id, actor_key_id (nullable), action, resource_type, resource_id, outcome, reason_code, client_ip_hash`.
  Events: `LINK_CREATED`, `LINK_DELETED`, `LINK_DISABLED`, `API_KEY_CREATED`, `API_KEY_REVOKED`, `AUTH_FAILED`, `ACCESS_DENIED`, `RATE_LIMITED`, `URL_REJECTED`.
  Successful redirects are not audited (volume); they become click events instead.
  State changes write their audit row in the same transaction.
- **S-11 Append-only.** The application DB role has `INSERT` and `SELECT` only on `audit.audit_events`, granted in a Flyway migration and verified by an integration test that attempts `UPDATE` and `DELETE`. Stretch: hash-chain rows for tamper evidence.

## 7. Controls — data handling, supply chain, runtime and architecture
- **S-12 Log hygiene.** Structured JSON with `requestId`. Never log API keys, full target URLs, raw client IPs or request bodies. Log short code, key ID and a salted IP hash.
- **S-13 Dependency scanning.** OWASP Dependency-Check fails on CVSS ≥ 7 unless a suppression records a justification and an expiry date.
- **S-14 Secret scanning.** gitleaks before commit and in the full gate. Secrets only from environment variables; only `.env.example` is committed; dev credentials in `docker-compose.yml` are marked dev-only.
- **S-15 Container hardening.** Minimal JRE base image, non-root user, read-only root filesystem where possible.
- **S-16 Safe errors.** RFC 9457 problem details with stable error codes; no stack traces, SQL or class names in responses.
- **S-17 Actuator exposure.** Only `health`, `info` and `prometheus`, on a separate management port not exposed publicly.
- **S-18 Analytics privacy (spec 03).** No raw IPs stored. Retention, bot handling and stats visibility are decided in spec 03.
- **S-19 Cache consistency (spec 02).** Delete and disable evict the cache entry; TTL as backstop. Redis unavailable → read the database (fail open on cache, never on auth or policy).
- **S-20 Module isolation.** Each module owns its Postgres schema; ArchUnit forbids access to another module's `domain`, `adapter` or persistence classes. In the monolith one DB role serves all modules; per-module DB roles arrive when a module is extracted (§10).

Security response headers on all API responses: `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`. HSTS is set at the TLS terminator.

## 8. Controls — secure AI-assisted development
- **S-21 AI usage guardrails.**
  - Agents are denied read access to `.env` files and secrets (`.claude/settings.json` deny rules; reviewer agent runs read-only).
  - Requirements are given to agents in the engineer's own words; internal source documents are not pasted into external tools.
  - No generated code is committed without engineer review of the diff; high-impact areas (AGENTS.md §13) need an approved plan first.
  - A second agent reviews each commit against this file; findings and decisions are recorded in `docs/AI_LOG.md`.
  - Generated code passes the same gates as human code: tests, ArchUnit, static analysis, dependency and secret scans.
  - Residual risk: path deny rules do not cover shell commands the engineer approves. Mitigations: no real secrets in the project folder, every shell command reviewed before approval, reviewer agent read-only by instruction (Codex in IDE; a sandboxed CLI is the stronger option).

## 9. Verification
| Gate | How | When |
|---|---|---|
| Negative security tests per control | JUnit, named `S-xx: ...` | Every `./gradlew check` |
| Append-only audit | Testcontainers integration test | Every `./gradlew check` |
| Module isolation and layering | ArchUnit | Every `./gradlew check` |
| API compatibility | oasdiff against `api/openapi.yaml` | Every `./gradlew check` |
| Static analysis | SpotBugs, Checkstyle | Every `./gradlew check` |
| Dependencies | OWASP Dependency-Check | When dependencies change, and before submission |
| Secrets | gitleaks | Before each commit, and before submission |
| Human review | Engineer review of every diff; second-agent review of every commit | Every task |

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

Residual risk accepted: a public phishing URL that passes policy can be shortened. Mitigation: takedown via `LINK_DISABLED` (spec 02), which is audited and evicts the cache.
