# 01 Core shortener
Mode: A (new)
Status: Approved

## Problem
API clients need to turn long URLs into short codes and manage their own links. Visitors who follow a short code must be redirected safely. Attackers commonly abuse URL shorteners: to hide phishing, script or internal-network targets behind a trusted domain, to tamper with other clients' links, and to enumerate them. Every state change must therefore be audited, and the security baseline in `docs/SECURITY.md` applies from the first release.

## Goals
- G1: An admin can issue API keys to clients without any user-account system.
- G2: A client can create, read and delete its own short links, and no one else's.
- G3: A visitor following a valid short code is redirected with `302` to a target that passed the URL policy.
- G4: Every state change and every security-relevant rejection leaves an append-only audit record, mirrored to a log stream that a SIEM can ingest.
- G5: One command runs the whole system, and one build command verifies it: tests, architecture rules, formatting, coverage and API compatibility.

## Non-goals
- Redis cache and link disable / takedown (spec 02).
- Per-client-IP rate limiting (moved to spec 02; see Follow-ups).
- Click events and click analytics (spec 03). Spec 01 publishes no click events.
- Custom aliases, link expiry, editing a link's target.
- User accounts, login UI, OAuth/OIDC (D4).
- Listing a client's links (`GET /api/links`).
- Prometheus metrics endpoint.
- Distributed tracing.
- Distributed rate limiting (SECURITY.md §10).

## Actors
- **Admin:** holds the bootstrap admin key. Issues owner keys.
- **Owner (API client):** holds an owner key. Manages its own links.
- **Visitor:** anonymous. Follows short links.

## Requirements

### API keys and authentication
- R1: The system shall accept a bootstrap admin key whose SHA-256 hash is configured in the `BOOTSTRAP_ADMIN_KEY_HASH` environment variable. The plaintext admin key never enters the application's configuration. `scripts/new-admin-key.sh` generates an admin key and prints the key and its hash. No secret is seeded through a database migration.
- R2: The admin shall be able to create an owner key via `POST /api/keys`. The full key is returned once in the creation response and is never retrievable again. Only its SHA-256 hash and a public key-ID prefix are stored.
- R3: Every `/api/**` request shall require a valid, non-revoked API key. Keys are compared in constant time.
- R4: Key endpoints shall be admin-only. Link endpoints shall be owner-only.
- R5: API keys shall be stored in their own `identity` schema, owned by the shared kernel. `links.owner_key_id` stores the owner's key ID, with no foreign key across schemas. The link module never reads `identity` tables.

### Links
- R6: An owner shall be able to create a short link for a target URL. Each creation produces a new code, even for a target the owner has shortened before (no deduplication).
- R7: Short codes shall be 7 characters of Base62 from a cryptographically secure random source. Uniqueness is enforced by the database. On collision the system retries up to 3 times, then fails.
- R8: Every target URL shall pass the URL policy (SECURITY.md §4, S-01 to S-05) before it is stored. The stored target is the normalized form.
- R9: An owner shall be able to read the metadata of its own link via `GET /api/links/{code}`.
- R10: An owner shall be able to delete its own link via `DELETE /api/links/{code}`. Deletion is a soft delete (status `DELETED`). A deleted code is never reused.
- R11: Any attempt to read or delete a link that does not exist, is deleted, or belongs to another key shall return `404`. Responses never reveal that a code exists or once existed.

### Redirect
- R12: `GET /{code}` shall respond `302 Found` with `Location` set to the stored target when the code identifies an active link.
- R13: `GET /{code}` shall respond `404` for unknown, deleted or malformed codes, with the same response for all three.
- R14: The redirect module shall resolve codes only through the link module's `LinkLookup` API. A successful redirect performs no writes.

### Rate limiting
- R15: Link creation shall be rate-limited per API key. The limit is configurable, with a default of 60 per minute. Every authenticated `POST /api/links` attempt counts toward the limit, including attempts rejected for validation or by the URL policy. A request rejected with `413` (R30) does not count.
- R16: Exceeding the limit shall return `429` with a `Retry-After` header and write a `RATE_LIMITED` audit event.

### Audit
- R17: The system shall write an audit event for `API_KEY_CREATED`, `LINK_CREATED`, `LINK_DELETED`, `AUTH_FAILED`, `ACCESS_DENIED`, `RATE_LIMITED` and `URL_REJECTED`, with the fields defined in S-10.
- R18: A state change and its audit event shall commit in the same transaction. If the audit write fails, the state change is rolled back.
- R19: Audit events shall be append-only. The application's database user cannot update or delete them.
- R20: If the audit write for a rejection fails (`AUTH_FAILED`, `ACCESS_DENIED`, `RATE_LIMITED`, `URL_REJECTED`), the request shall still be rejected with its original response, and the audit failure shall be logged. A rejection never fails open.

### Database access
- R21: The system shall use two database users:
  - a **migration user** that owns the `identity`, `link` and `audit` schemas and runs Flyway;
  - an **application user** that serves requests with only the grants it needs. It has no DDL rights and only `INSERT` and `SELECT` on `audit.audit_events`.

  Grants are applied by Flyway migrations. Docker Compose creates both users. Flyway runs as a separate one-shot step in Docker Compose, before the application starts. The application container receives only the application user's credentials.

### Configuration
- R22: `PUBLIC_BASE_URL`, `IP_HASH_SALT`, `BOOTSTRAP_ADMIN_KEY_HASH` and the database credentials shall come from environment variables. No default values exist in code. If any required variable is missing, the application shall refuse to start.

### Logging
- R23 (deferred by D15, see Follow-ups): Log levels shall be the following. Levels follow this policy by convention but are not test-enforced.
  - **ERROR:** unexpected failures (any `5xx`);
  - **WARN:** security rejections and degradation (`AUTH_FAILED`, `ACCESS_DENIED`, `RATE_LIMITED`, `URL_REJECTED`, audit write failed);
  - **INFO:** state changes and startup;
  - **DEBUG:** off by default, and never carries sensitive data.
- R24: Logs shall use Spring Boot's structured logging in ECS format, with `requestId` on every line written while serving a request.
- R25 (deferred by D15, see Follow-ups): Every audit event shall also be written to the log stream as one structured line, with the same fields as the audit row and nothing sensitive. The database table remains the source of truth. A mirror line for a state change is written only after its transaction commits.
- R26: Logs shall never contain API keys, full target URLs, raw client IPs or request bodies. The short code, key ID and salted IP hash may be logged.

### Cross-cutting
- R27: Every response shall carry an `X-Request-Id` header. A valid incoming `X-Request-Id` is reused; otherwise one is generated. The request ID is recorded in logs and audit events.
- R28: All error responses shall be RFC 9457 `application/problem+json` with a stable error code (see Error codes) and a `requestId` property. They never contain stack traces, SQL, class names or internal messages.
- R29: All `/api/**` responses shall carry `X-Content-Type-Options: nosniff`, `Cache-Control: no-store` and `Referrer-Policy: no-referrer`. Redirect responses carry `Cache-Control: no-store`.
- R30: Request bodies on `/api/**` shall be capped at 8 KB. Larger bodies are rejected with `413` and are not processed.
- R31: The actuator shall run on a separate management port and expose only `health` and `info`.
- R32: The service shall serve its OpenAPI document. Swagger UI shall be enabled only in a `local` profile and is off by default.
- R33: `docker compose up --build` shall start the application and PostgreSQL 16. The application container runs as a non-root user on a minimal JRE image, with a read-only root filesystem and a writable `tmpfs` for `/tmp`. All configuration is documented in `.env.example`.
- R34: `./gradlew check` shall run unit tests, integration tests, ArchUnit, Spotless, JaCoCo coverage (80% line on `domain` packages) and the oasdiff compatibility check against the committed `api/openapi.yaml`.

## API contract (behavioural summary)
The exact OpenAPI is written to `api/openapi.yaml` in the plan phase.

| Method and path | Caller | Success | Body (success) |
|---|---|---|---|
| `POST /api/keys` | Admin | `201` | `{ id, key, createdAt }`. `key` appears only here. |
| `POST /api/links` | Owner | `201`, `Location: /api/links/{code}` | `{ code, shortUrl, targetUrl, createdAt }` |
| `GET /api/links/{code}` | Owner | `200` | `{ code, shortUrl, targetUrl, status, createdAt }` |
| `DELETE /api/links/{code}` | Owner | `204` | none |
| `GET /{code}` | Visitor | `302`, `Location: <target>` | none |
| OpenAPI document (path per A11) | Anyone | `200` | OpenAPI JSON |

The request body for `POST /api/links` is `{ "targetUrl": "<string>" }`. Unknown fields are ignored.

### Error codes
Every error body carries `type`, `title`, `status`, `code` and `requestId`.

| HTTP | Code | When |
|---|---|---|
| 400 | `validation-failed` | Missing or malformed request body |
| 400 | `url-rejected` | Target fails the URL policy; the response includes a `reason` (see below) |
| 401 | `unauthorized` | Missing, unknown, malformed or revoked API key |
| 403 | `forbidden` | Valid key with the wrong role for the endpoint |
| 404 | `not-found` | Unknown, deleted, malformed or other owner's code |
| 413 | `payload-too-large` | Request body on `/api/**` over 8 KB |
| 429 | `rate-limited` | Creation limit exceeded; `Retry-After` set |
| 500 | `internal-error` | Unexpected failure, including a failed audit write during a state change (AC30) |
| 503 | `code-generation-failed` | Short-code collision after 3 retries |
| 503 | `service-unavailable` | Database unavailable |

URL policy `reason` values: `SCHEME_NOT_ALLOWED`, `HOST_NOT_ALLOWED`, `USERINFO_NOT_ALLOWED`, `SELF_REFERENCE`, `MALFORMED`, `TOO_LONG`.

## Acceptance criteria

### API keys and authentication
- AC1 (R1, R2): Given an admin key generated by `scripts/new-admin-key.sh`, with its hash configured as `BOOTSTRAP_ADMIN_KEY_HASH`, when the admin calls `POST /api/keys`, then:
  - the response is `201` with `id`, `key` and `createdAt`;
  - the database holds only the key's hash and prefix;
  - an `API_KEY_CREATED` audit event with outcome `SUCCESS` is written.
- AC2 (R2): Given an owner key was created, when later responses (including `GET` calls) and log output are inspected, then the plaintext key does not appear.
- AC3 (R3): Given no API key, an unknown key, a malformed key or a revoked key, when any `/api/**` endpoint is called, then the response is `401 unauthorized` and an `AUTH_FAILED` audit event is written.
- AC4 (R4): Given an owner key, when it calls `POST /api/keys`, then the response is `403 forbidden` and an `ACCESS_DENIED` audit event is written.
- AC5 (R4): Given the admin key, when it calls any `/api/links` endpoint, then the response is `403 forbidden` and an `ACCESS_DENIED` audit event is written.
- AC6 (R1): Given the Flyway migrations, when they are inspected, then none inserts an API key or any other secret.
- AC7 (R5, S-20): Given the database and codebase, when they are inspected, then:
  - `api_keys` lives in the `identity` schema;
  - `links.owner_key_id` has no foreign key to another schema;
  - the architecture tests show that the link module has no dependency on identity persistence classes.

### Links
- AC8 (R6, R7): Given an owner key and an allowed target, when the owner calls `POST /api/links`, then:
  - the response is `201`;
  - `code` is 7 Base62 characters;
  - `shortUrl` is `PUBLIC_BASE_URL` + `/` + `code`;
  - `targetUrl` is the normalized target;
  - a `LINK_CREATED` audit event is written in the same transaction.
- AC9 (R6): Given an owner has already shortened a target, when it shortens the same target again, then a new, different code is returned.
- AC10 (R7): Given the code generator returns colliding codes twice and then a free code, when a link is created, then creation succeeds with the free code.
- AC11 (R7): Given the code generator returns colliding codes on all 4 attempts (1 + 3 retries), when a link is created, then the response is `503 code-generation-failed` and no link is stored.
- AC12 (R8, S-01): Given a target whose scheme is not `http` or `https` (including `javascript:`, `data:`, `file:`, `ftp:` and mixed-case `JaVaScRiPt:`), when a link is created, then the response is `400 url-rejected` with reason `SCHEME_NOT_ALLOWED`, and a `URL_REJECTED` audit event is written.
- AC13 (R8, S-02): Given a target host that is `localhost` or a variant of it, or a literal IP (in any of the decimal, octal, hex or short-form encodings) in one of these ranges: loopback, private, link-local, CGNAT, unique-local IPv6 or unspecified. When a link is created, then the response is `400 url-rejected` with reason `HOST_NOT_ALLOWED`.
- AC14 (R8, S-03): Given a target containing userinfo (`user@` or `user:pass@`), when a link is created, then the response is `400 url-rejected` with reason `USERINFO_NOT_ALLOWED`.
- AC15 (R8, S-04): Given a target whose host is the host of `PUBLIC_BASE_URL`, when a link is created, then the response is `400 url-rejected` with reason `SELF_REFERENCE`.
- AC16 (R8, S-05): Given a target that does not parse, or that contains control characters, whitespace or CR/LF, when a link is created, then the response is `400 url-rejected` with reason `MALFORMED`. Given a target over 2,048 characters, then the reason is `TOO_LONG`.
- AC17 (R8, S-05): Given a target with an internationalized host, when a link is created, then the stored and returned `targetUrl` uses the punycode host.
- AC18 (R9): Given an owner's active link, when the owner calls `GET /api/links/{code}`, then the response is `200` with `code`, `shortUrl`, `targetUrl`, `status` = `ACTIVE` and `createdAt`.
- AC19 (R10): Given an owner's active link, when the owner calls `DELETE /api/links/{code}`, then the response is `204`, the link's status becomes `DELETED`, and a `LINK_DELETED` audit event is written in the same transaction.
- AC20 (R11, S-08): Given a link owned by key A, when key B calls `GET` or `DELETE /api/links/{code}`, then:
  - the response is `404 not-found`, identical to the response for an unknown code;
  - the link is unchanged;
  - an `ACCESS_DENIED` audit event is written.
- AC21 (R10, R11): Given a deleted link, when its owner calls `GET` or `DELETE` on it, then the response is `404 not-found`, identical to the response for an unknown code. No second `LINK_DELETED` event is written.
- AC22 (R10): Given a deleted link's code, when new links are created, then that code is never issued again. Deleted rows keep their code, so the database unique constraint blocks reuse.

### Redirect
- AC23 (R12): Given an active link, when a visitor calls `GET /{code}`, then the response is `302` with `Location` equal to the stored target and `Cache-Control: no-store`.
- AC24 (R13): Given an unknown code, a deleted code, or a malformed code (wrong length or non-Base62 characters), when a visitor calls `GET /{code}`, then each response is `404 not-found` with the same status, headers and body shape.
- AC25 (R14, S-20): Given the codebase, when the architecture tests run, then `redirect` depends on `link` only through `link.api`. Given a successful redirect, then no database row is written.

### Rate limiting
- AC26 (R15, R16, S-09): Given an owner key that has made 60 creation attempts in the current minute, when it makes one more, then:
  - the response is `429 rate-limited` with `Retry-After`;
  - no link is stored;
  - a `RATE_LIMITED` audit event with the key's ID is written.
- AC27 (R15): Given the limit is configured to a different value, when it is exercised, then the configured value applies.
- AC28 (R15): Given two owner keys, when one exhausts its creation limit, then the other can still create links.
- AC29 (R15, S-09): Given an owner key has made 60 creation attempts in the current minute that were rejected by the URL policy or by validation, when it submits an allowed target, then the response is `429 rate-limited`.

### Audit
- AC30 (R18, S-10): Given the audit write fails during link creation or deletion, when the use case runs, then the link change is rolled back and the client receives an error.
- AC31 (R19, R21, S-11): Given the application database user, when it attempts `UPDATE` or `DELETE` on `audit.audit_events`, then the database rejects the statement.
- AC32 (R17, S-10): Given any audited event, when its row is read, then:
  - it has `id`, `occurred_at` (from the injected clock), `request_id`, `actor_key_id` (null when unauthenticated), `action`, `resource_type`, `resource_id`, `outcome`, `reason_code` and `client_ip_hash`;
  - it contains no API key, full target URL or raw IP.
- AC33 (R20): Given the audit sink fails, when a request is rejected for an invalid key, the wrong role or owner, the rate limit or the URL policy, then:
  - the response is the same rejection as with a working sink (`401`, `403`/`404`, `429` or `400`);
  - no state changes;
  - a WARN line records the audit failure, with no secret, full URL or raw IP.

### Database access
- AC34 (R21): Given the application database user, when it attempts `CREATE`, `ALTER` or `DROP` in the `identity`, `link` or `audit` schemas, then the database rejects the statement.
- AC35 (R21): Given `docker compose up --build`, when the system is running, then:
  - both database users exist;
  - Flyway ran as the migration user, in a one-shot step that completed before the application started;
  - the application container's environment holds only the application user's database credentials, and its connections run as that user.

### Configuration
- AC36 (R22): Given any required variable is missing (`PUBLIC_BASE_URL`, `IP_HASH_SALT`, `BOOTSTRAP_ADMIN_KEY_HASH` or a database credential), when the application starts, then startup fails with a clear message naming the missing variable (never its value).

### Logging
- AC37 (R23) (deferred by D15): Given these flows, when logs are captured, then each is logged at the stated level, and no DEBUG lines appear with the default configuration:

  | Flow | Level |
  |---|---|
  | A `5xx` failure | ERROR |
  | Auth failure, access denied, rate limit, URL rejection, audit write failure | WARN |
  | Link created, link deleted, key created, startup | INFO |
- AC38 (R24): Given any log line written while serving a request, when it is parsed, then it is valid ECS JSON (`@timestamp`, `log.level`, `message` and so on) and includes `requestId`.
- AC39 (R25) (deferred by D15): Given each audited action, when logs are captured, then one structured line carries the same fields as the audit row. Given a state change whose transaction rolls back, then no mirror line is written for it.
- AC40 (R26, S-12): Given the flows in AC1, AC3, AC8, AC23 and AC26, when log output is captured, then it contains no API key, full target URL, raw client IP or request body.

### Cross-cutting
- AC41 (R27): Given a request with a valid `X-Request-Id`, when it is processed, then the response echoes that ID, and audit events and log lines record it. Given a missing or invalid one (see A5), then a new ID is generated and returned.
- AC42 (R28, S-16): Given any error, including an unexpected exception, a database outage and a `413`, when the response is inspected, then:
  - it is `application/problem+json` with a stable `code`;
  - its `requestId` equals the response's `X-Request-Id`;
  - it contains no stack trace, SQL, class name or exception message.
- AC43 (R29): Given any `/api/**` response, when headers are inspected, then `X-Content-Type-Options: nosniff`, `Cache-Control: no-store` and `Referrer-Policy: no-referrer` are present.
- AC44 (R30): Given a request to any `/api/**` endpoint with a body over 8 KB, when it is sent, then the response is `413 payload-too-large`, and no link or key is created.
- AC45 (R31, S-17): Given the running application, when the public port is probed for `/actuator/**`, then nothing is served. On the management port, only `health` and `info` are exposed.
- AC46 (R32): Given the default configuration, when the OpenAPI document is requested, then it is served, and Swagger UI returns `404`. Given the `local` profile, then Swagger UI is available.
- AC47 (R33, S-15): Given a clean checkout with `.env` created from `.env.example`, when `docker compose up --build` runs, then:
  - the app and PostgreSQL start;
  - the app container runs as a non-root user on a minimal JRE image, with `read_only: true` and a writable `tmpfs` for `/tmp`;
  - a create → redirect → delete round trip works.
- AC48 (R34): Given the repository, when `./gradlew check` runs, then all gates in R34 run and pass. A breaking change to `api/openapi.yaml` fails the build.

## Edge cases and error handling
- **Route precedence:** `/api/**` and the OpenAPI and Swagger UI paths take precedence over `/{code}`. Codes are exactly 7 Base62 characters in a single path segment, so none of those paths can be a code. The actuator is on a separate port.
- **Query string or trailing slash on `GET /{code}`:** the query string is ignored. A trailing slash makes the code malformed, so the response is `404`.
- **Second `DELETE` on the same link:** returns `404` (R11) and writes no second `LINK_DELETED` event.
- **Concurrent creates generating the same code:** the database constraint rejects one, and that request retries (R7).
- **Database unavailable:** the management API and redirects return `503 service-unavailable` (ARCHITECTURE §9). Spec 01 has no cache. Audit writes for rejections also fail; R20 applies.
- **Revoked key:** treated like an unknown key (`401`).
- **Target URL with a fragment:** allowed and preserved.
- **Body exactly 8 KB:** accepted. The 2,048-character target limit fits well inside the cap.

## Non-functional requirements
- **Security:** controls S-01 to S-08, S-10 to S-12, S-14 to S-17 and S-20 apply. S-09 applies to link creation only (see Deviations). Each control has at least one negative test named `S-xx: ...`. S-13 runs as the manual pre-release gate (D13).
- **Architecture:** the link module, redirect module and shared kernel follow AGENTS.md §6. ArchUnit enforces module boundaries, inward dependencies, and `app` as the only wiring point (S-20). Schema ownership: the link module owns `link`, and the shared kernel owns `identity` and `audit`. There are no cross-schema foreign keys or joins.
- **Least privilege:** the request-serving database user has no DDL rights and append-only access to audit (R21).
- **Determinism:** time comes from an injected `Clock` and randomness from `ShortCodeGenerator`.
- **Performance:** a successful redirect is one read through `LinkLookup` with no writes.
- **Compatibility:** the API is versionless. The committed `api/openapi.yaml` is the baseline from this spec onward (D6).
- **Observability:** ECS structured logs with `requestId` and the levels in R23 (by convention, not test-enforced); audit mirroring deferred (D15). Only the short code, key ID and salted IP hash are logged.
- **Testability:**
  - Domain logic is covered by unit tests with in-memory port fakes.
  - Integration tests run on Testcontainers Postgres.
  - Line coverage on `domain` packages is at least 80%.
- **Dependencies:** no metrics or tracing dependency is added in spec 01. ECS logging uses Spring Boot's built-in support. springdoc (already in D2) serves the OpenAPI document. Boot 4 compatibility of springdoc and oasdiff is verified in the plan.

## Known risks and limitations
- **L1 `AUTH_FAILED` audit volume.** Each request with a bad or missing key writes an audit row, and no limit applies before authentication. An unauthenticated caller can inflate audit writes and table size. The per-IP limit in spec 02 closes this, so that limit must cover unauthenticated `/api/**` requests as well as redirects.
- **L2 Client IP behind Docker networking.** Behind Docker's port publishing or NAT, the socket's remote address may be the same for every client. In spec 01 this makes `client_ip_hash` in audit rows less useful. For spec 02, a per-IP limit would throttle all clients as one. Closing this needs the trusted-proxy follow-up.
- **L3 In-memory rate limiter.** Limits are per instance (SECURITY.md §10).
- **L4 Rejection evidence when the audit write fails.** Under R20 a rejection whose audit write fails is recorded only in the log stream. Until the audit mirror follow-up lands (D15), the evidence is only the WARN line; after it lands, the R25 mirror line is added. The database has no row for it.
- **L5 Code enumeration before spec 02.** Redirects have no per-IP rate limit until spec 02, so enumeration (T6) is limited only by the size of the code space (62⁷ ≈ 3.5 × 10¹²).

## Deviations from current docs
These project documents disagree with this spec. At Gate 1 the engineer asked for them to be fixed in a separate docs change, reviewed on its own (AGENTS.md §14).
- **SECURITY.md S-09** says rate limiting covers both link creation and redirects in spec 01. Per-IP limiting now moves to spec 02 and should also cover unauthenticated `/api/**` traffic (L1). S-09 and the T9 status need updating.
- **AGENTS.md §9** requires rate limiting of redirects per client IP. That moves to spec 02 with S-09 (see L5).
- **SECURITY.md S-14** says gitleaks runs "in the full gate". gitleaks stays a separate command, not part of `./gradlew check`. S-14 should say "before each commit and before submission", matching SECURITY.md §9.
- **SECURITY.md S-17** lists `prometheus` as exposed. Spec 01 exposes only `health` and `info`.
- **ARCHITECTURE.md §5** needs a `POST /api/keys` row (admin) and the OpenAPI document path. §12's two open items are resolved by this spec.
- **ARCHITECTURE.md §7** puts `api_keys` in the `link` schema, with `owner_key_id` as a foreign key. Correction: `api_keys` moves to the `identity` schema (shared kernel), and `owner_key_id` becomes a plain key ID with no cross-schema foreign key.
- **ARCHITECTURE.md §4** should show the `identity` schema under the shared kernel.
- **ARCHITECTURE.md §8** should mention the two database users.

## Open questions and assumptions
Your answers settled these: key issuance, unknown vs deleted codes, soft delete, deduplication, creation rate limit, configuration, scope, database users, schema ownership, logging and error bodies. A1, A2 and A10 were confirmed at Gate 1. The other assumptions were accepted with the spec.

- A1 **Admin key environment variable (confirmed at Gate 1).** The variable holds only the SHA-256 hash of the admin key (`BOOTSTRAP_ADMIN_KEY_HASH`), so the plaintext never enters the app's configuration. `scripts/new-admin-key.sh` generates a key and prints the key and its hash (R1).
- A2 **Key revocation (confirmed at Gate 1).**
  - Spec 01 rejects revoked keys (AC3) but adds no revoke endpoint.
  - `API_KEY_REVOKED` stays unused until a revoke endpoint is specified.
  - Until then, revocation is a manual database operation. The revoke endpoint is a follow-up.
- A10 **Where Flyway runs (confirmed at Gate 1).**
  - In Docker Compose, Flyway runs as a separate one-shot step, and the application container receives only the application user's credentials (R21).
  - Integration tests run Flyway in the test setup as the migration user, then check the application user's restrictions (AC31, AC34) over a separate connection.
- A3 **Admin key scope.**
  - The admin key only manages keys. It has no `api_keys` row, can't own links, and gets `403` on link endpoints (AC5).
  - In audit events it appears under a fixed, reserved actor ID.
  - Wrong role returns `403`, while wrong owner returns `404` (AGENTS.md §7). Both are audited as `ACCESS_DENIED`.
- A4 **Key transport.**
  - Keys are sent as `Authorization: Bearer <key>`.
  - Each key carries a public key-ID prefix, used to look the key up before the constant-time hash comparison (S-07).
  - The exact key format is set in the plan.
- A5 **Valid `X-Request-Id`.** 1–64 characters of `[A-Za-z0-9._-]`. Anything else is replaced with a generated ID, which prevents log injection.
- A6 **Client IP for audit hashing.** `client_ip_hash` is computed from the socket's remote address, salted with `IP_HASH_SALT`. `X-Forwarded-For` is ignored, because trusting it by default lets callers choose their own IP. See L2.
- A7 **Owner sees `status`.** `GET /api/links/{code}` returns `status`, which is always `ACTIVE` in spec 01. Including it now makes spec 02's `DISABLED` an additive change.
- A8 **No click events.** The `EventPublisher` port and click events arrive with spec 03, which adds the consumer.
- A9 **`POST /api/keys` takes no body.** Labels and metadata on keys are out of scope.
- A11 **OpenAPI document.**
  - It is served unauthenticated at springdoc's default path (`/v3/api-docs`), outside `/api/**`.
  - It describes the public contract, which isn't secret.
  - The plan decides whether to serve the committed `api/openapi.yaml` or a generated document checked against it.
- A12 **Audit mirror lines** (applies when the audit mirror follow-up is built). They use a dedicated logger name (for example `AUDIT`) so a SIEM can filter them, at the level R23 assigns: INFO for state changes, WARN for rejections.
- A13 **Audit values and the 401 challenge (confirmed after T2).**
  - Audit `outcome` is `SUCCESS` for state changes and `REJECTED` for rejections.
  - `AUTH_FAILED` reason codes are `MISSING`, `MALFORMED`, `UNKNOWN` and `REVOKED`. `ACCESS_DENIED` for a wrong role uses `WRONG_ROLE`.
  - `WRONG_ROLE` covers any `/api` route that no role is granted, including routes with no rule (default-deny, T3).
  - `ACCESS_DENIED` for another key's link uses `NOT_OWNER` (confirmed after T3).
  - `resource_type` is `API_KEY` or `LINK`.
  - `401` responses include `WWW-Authenticate: Bearer`.

## Follow-ups (not in spec 01)
- **Per-client-IP rate limiting** (spec 02). It covers redirects and unauthenticated `/api/**` traffic (L1). It needs the S-09 update and a decision on audit volume under a flood (one row per rejected request, or one per IP per window).
- **Trusted-proxy configuration for client IP** (A6, L2). This is a prerequisite for an effective per-IP limit behind Docker or a load balancer.
- **Distributed tracing** (W3C `traceparent` / OpenTelemetry), when services are extracted (D7 stage C).
- `GET /api/links` to list an owner's links, paginated. This is an additive change.
- Prometheus metrics endpoint.
- Key revocation endpoint (A2).
- Hash-chained audit rows for tamper evidence (S-11 stretch).
- **Audit mirror to the log stream** (R25, AC39, A12), cut by D15. A `MirroringAuditSink` decorator writing one `AUDIT` logger line per event, after commit for state changes. Until then a SIEM reads audit from the database, and L4's evidence is only the WARN line.
- **Log-level tests** (R23, AC37), cut by D15. Levels follow R23 by convention but are not test-enforced.
- **Exact-match OpenAPI drift check** (R34, AC48), cut by D15. Restore an `oasdiff diff` check that fails when the generated document and the committed `api/openapi.yaml` differ at all. Until then the build fails only on breaking changes, and reviewers check that API changes update `api/openapi.yaml`.