# 02 Daily link quota per API key
Mode: B-enhancement (existing code: `v1-baseline`, see `current-behavior.md`)
Status: Approved (Gate 1, engineer, 2026-10-07)
Decision: D16. Controls: S-09 (rate limiting), S-10 (audit), S-16 (safe errors).

## Problem
The per-minute creation limit (spec 01 R15, default 30) protects the service from bursts, but nothing caps a key's total use. One owner key can create up to 43,200 links per UTC day at the default limit. A leaked or misused key can therefore fill the database and spread spam links through a trusted domain (T9) long before anyone notices. D16 adds a daily link quota per key, default 500, on top of the per-minute limit.

## Goals
- G1: Cap how many links one API key can create in one UTC day, with a sensible default and a configuration override.
- G2: Over-quota attempts are refused cleanly, tell the client when to retry, store nothing and leave an audit trail.
- G3: The quota holds across restarts and across instances, because it is counted from stored links rather than in memory.
- G4: Nothing else about link creation, the per-minute limit or the API contract changes.

## Non-goals
- Per-tier or per-key quota values (one value for all keys).
- A remaining-quota response header (for example `X-Quota-Remaining`) or a quota endpoint.
- Monthly or other period quotas.
- A strict, race-free quota under concurrency (see R9, L1).
- Changing the per-minute limit, the body cap or the order of authentication and body cap.
- Quotas on any endpoint other than `POST /api/links`.

## Requirements

### Counting
- R1: The system shall count, per API key, the links created by that key in the current UTC day. A UTC day runs from `00:00:00` UTC inclusive to the next `00:00:00` UTC exclusive, judged by the link's creation time from the service clock.
- R2: Every successfully created link counts, whatever its status now. A link deleted later the same day still counts.
- R3: Attempts that create no link do not count: requests rejected with `401`, `403`, `413`, `429` (per-minute or daily), `400 validation-failed`, `400 url-rejected` or `503`.
- R4: The count is derived from stored links (`link.links`). No separate counter is kept.

### Enforcement
- R5: When a key has already created `quota` links in the current UTC day, a further `POST /api/links` by that key shall be refused with `429`, `application/problem+json`, `code = quota-exceeded`, `type = urn:url-shortener:problem:quota-exceeded`, and the standard problem fields (`title`, `status`, `requestId`). No exception text appears in the body (S-16).
- R6: The `429 quota-exceeded` response shall carry `Retry-After`: the whole seconds from now until the next `00:00:00` UTC, rounded up, minimum 1.
- R7: A refused request stores no link and writes no `LINK_CREATED` event. It writes one `RATE_LIMITED` audit event with outcome `REJECTED`, `resource_type = LINK`, `resource_id` empty, `reason_code = DAILY_QUOTA`, the caller's key ID and the hashed client IP. As for every rejection (spec 01 R20), a failed audit write is logged and does not change the `429` response.
- R8: Checks on `POST /api/links` run in this order; the first that fails decides the response:
  1. body cap (`413`) — before authentication, unchanged from spec 01 R30 (Q1)
  2. authentication and role (`401` / `403`)
  3. per-minute limit (`429 rate-limited`)
  4. body validation (`400 validation-failed`) — unchanged position (Q2)
  5. **daily quota (`429 quota-exceeded`)**
  6. URL policy (`400 url-rejected`)
  7. create (`201`, or `503 code-generation-failed`)
- R9: Concurrent requests from the same key that arrive while it is one link below its quota may each pass the check, so the key may end the day one or two links over the quota. This is accepted (L1). Sequential requests never exceed the quota.
- R10: A key over its quota for today can create links again from `00:00:00` UTC the next day, subject to the per-minute limit.

### Configuration
- R11: The quota is set by the optional environment variable `LINK_DAILY_QUOTA`, a whole number of links per key per UTC day, default `500` when the variable is unset. A value that is not a whole number of at least 1 (including an empty value) stops startup with an error that names the variable but not its value (Q3).
- R12: Docker Compose passes `LINK_DAILY_QUOTA` to the application container, defaulting to 500 when unset or empty, as it does `LINK_CREATE_LIMIT_PER_MINUTE` (Q4).

### Data
- R13: A new Flyway migration `V4` adds an index that serves the count by key and creation time. Applied migrations `V1`–`V3` are not edited. The migration grants nothing new to the application role.

### Observability
- R14: A refused request logs one WARN line `Rate limited: reason=DAILY_QUOTA actorKeyId=<key ID>`, in the same shape as the per-minute line. No API key, target URL, raw IP or body is logged (S-12).

## Acceptance criteria
In these criteria "quota" is the configured `LINK_DAILY_QUOTA`; tests use a small value (for example 3) and a per-minute limit high enough not to interfere, and control the clock where a time is stated.

### Counting
- AC1 (R1, R5, S-09): Given an owner key has created `quota` links today, when it creates one more, then the response is `429 quota-exceeded` and the key's link count is still `quota`.
- AC2 (R1): Given an owner key has created `quota - 1` links today, when it creates one more, then the response is `201` (the quota-th link is allowed).
- AC3 (R2): Given an owner key has created `quota` links today and deleted some or all of them, when it creates one more, then the response is `429 quota-exceeded`.
- AC4 (R3): Given an owner key's attempts today were rejected by the URL policy, by validation, by the body cap and by the per-minute limit, when it then creates links, then it can create `quota` links before the first `quota-exceeded`.
- AC5 (R1, R10): Given an owner key created `quota` links on the previous UTC day, when it creates a link at `00:00:00` UTC today, then the response is `201`.
- AC6 (R1): Given an owner key created `quota` links today, when it creates a link at `23:59:59.999999` UTC, then the response is `429 quota-exceeded`; when it creates one at `00:00:00` UTC the next day, then the response is `201`.
- AC7 (R1, S-08): Given one owner key has used its quota today, when another owner key creates a link, then the response is `201`.

### Response and audit
- AC8 (R5, R6, S-16): Given an over-quota request at `22:00:00.250` UTC, when it is refused, then the body has exactly `type = urn:url-shortener:problem:quota-exceeded`, `title = Too Many Requests`, `status = 429`, `instance = /api/links`, `code = quota-exceeded` and the request's `requestId` (no `detail`, no other properties, the same shape as AC22), and the header `Retry-After: 7200`.
- AC9 (R6): Given an over-quota request at `23:59:59.999999` UTC, when it is refused, then `Retry-After` is `1`.
- AC10 (R7, S-10): Given an over-quota request, when it is refused, then exactly one audit row exists for its request ID: `action = RATE_LIMITED`, `outcome = REJECTED`, `actor_key_id` = the caller's key ID, `resource_type = LINK`, `resource_id` null, `reason_code = DAILY_QUOTA`, `client_ip_hash` present; and no `LINK_CREATED` row and no link was written.
- AC11 (R7): Given the audit sink fails, when an over-quota request is refused, then the response is still `429 quota-exceeded`.

### Check order
- AC12 (R8): Given an owner key is over both its per-minute limit and its daily quota, when it creates a link, then the response is `429 rate-limited` (not `quota-exceeded`) and the audit reason is `CREATE_LIMIT`.
- AC13 (R8): Given an owner key over its daily quota, when it sends a body without `targetUrl`, then the response is `400 validation-failed` (Q2).
- AC14 (R8): Given an owner key over its daily quota, when it submits a target the URL policy rejects, then the response is `429 quota-exceeded` and no `URL_REJECTED` event is written.
- AC15 (R8): Given an owner key over its daily quota, when it sends an oversized body, then the response is `413`; when it sends no or a bad API key, then the response is `401`.
- AC16 (R3, R8): Given an owner key over its daily quota, when it is refused with `quota-exceeded`, then that refusal still counts toward the per-minute limit (spec 01 R15 unchanged).

### Configuration and data
- AC17 (R11): Given `LINK_DAILY_QUOTA` is unset, when a key creates 500 links in a UTC day, then the 501st is refused with `quota-exceeded`. (Verified at the configuration level: the bound default is 500; a 500-request test is not required.)
- AC18 (R11): Given `LINK_DAILY_QUOTA` is configured to a different value, when it is exercised, then that value applies.
- AC19 (R11): Given `LINK_DAILY_QUOTA` is `0`, negative or not a whole number, when the application starts, then startup fails and the error names `LINK_DAILY_QUOTA` without echoing its value.
- AC20 (R13): Given the migrations have run, then an index on `link.links` covering `(owner_key_id, created_at)` exists, `V1`–`V3` checksums are unchanged, and the application role's privileges on `link.links` are unchanged.

### Must stay the same (characterization)
- AC21 (Delta): Every spec 01 test of link creation and the per-minute limit passes unchanged, including `CreationRateLimitIT` AC26–AC29 and R15, `CreateLinkIT`, `LinkServiceTest`, `RejectionAuditFailureIT` and `ApiContractIT`.
- AC22 (Delta): The `429 rate-limited` body keeps exactly its spec 01 shape: `type`, `title`, `status`, `instance = /api/links` (added by Spring MVC; observed in T1), `code = rate-limited`, `requestId`, no `detail`, no other properties.

## Edge cases and error handling
- **Database unavailable during the count:** the request fails closed with `503 service-unavailable` (existing handler, ARCHITECTURE §9). No link is created. The quota never fails open.
- **Quota lowered by configuration below today's count** (after a restart): the key is refused until the next UTC day. No error, no special case.
- **Quota raised:** the key can create up to the new value immediately.
- **Code-generation failure (`503`) after the quota check passes:** no link stored, so nothing counted (R3).
- **Clock:** the UTC day comes from the service's injected `Clock`, never the database clock, so the check and `created_at` use the same time source.
- **Two instances:** both count the same rows, so the quota is shared. The per-minute limit stays per instance (spec 01 L3).
- **Admin key:** cannot create links (spec 01 A3), so the quota never applies to it.

## Non-functional requirements
- **Security (S-09, S-10, S-12, S-16):** per-key quota; audited refusal with reason `DAILY_QUOTA`; no secrets or URLs in logs; no internals in the error body. Tests for each refusal path are named with the control they cover. Negative tests: over quota, deleted links, boundary second, wrong config.
- **Performance:** one indexed count query per create that passes the per-minute limit and validation. With the `V4` index the count reads at most `quota + 2` index entries for one key.
- **Compatibility (D6):** additive only. The OpenAPI `429` response on `POST /api/links` already exists with `Retry-After` and problem+json; only its description changes to cover both reasons. `oasdiff` must report no breaking change. Clients that handle `429` with `Retry-After` keep working; `quota-exceeded` is a new `code` value, which tolerant readers accept.
- **Auditability:** every refusal produces a `RATE_LIMITED` row; the reason distinguishes per-minute (`CREATE_LIMIT`) from daily (`DAILY_QUOTA`).
- **Architecture:** conforms to ARCHITECTURE.md and DECISIONS.md (D3 Postgres as source of truth, D14 `JdbcClient`, D16). The count is a link-module concern inside the link module's own schema.

## Delta
- **Changes**
  - New refusal `429 quota-exceeded` with `Retry-After` to next UTC midnight, audited as `RATE_LIMITED` / `DAILY_QUOTA`.
  - New optional variable `LINK_DAILY_QUOTA` (default 500), passed through Docker Compose.
  - New migration `V4` adding an index on `link.links`.
  - New error-code row in the spec's error table: `429 quota-exceeded`, "Daily link quota exceeded; `Retry-After` set to the next 00:00 UTC".
  - OpenAPI `429` description on `POST /api/links` widened from "Creation limit reached" to cover both reasons (non-breaking).
  - Docs: ARCHITECTURE.md create-link flow and §9 failure modes; SECURITY.md T9 status (engineer edits, Q6).
- **Must stay the same**
  - `201` create response, body, `Location` and `LINK_CREATED` event.
  - Per-minute limit: value, default 30, counting rules, `429 rate-limited` body and `Retry-After`, audit reason `CREATE_LIMIT`.
  - The `413` body cap before authentication, `401` / `403` behavior, `400 validation-failed`, `400 url-rejected`, `503 code-generation-failed`.
  - Soft delete semantics; codes are never reused.
  - Every other endpoint; applied migrations `V1`–`V3`; database grants.

## Known risks and limitations
- **L1 Concurrent overshoot (accepted).** The count and the insert are not serialized per key, so parallel requests at the limit can exceed the quota by one or two links. Bounded in practice by the per-minute limit and request concurrency. A strict quota would need a per-key lock or counter row; not justified for this threat.
- **L2 Deleted links still use quota.** Intentional (R2): otherwise a key could create, delete and repeat without limit.
- **L3 UTC days only.** Clients in other time zones see the reset at their local equivalent of 00:00 UTC.

## Resolved at Gate 1 (engineer, 2026-10-07)
- **Q1 Body cap vs authentication order.** Keep today's order: the body cap runs before authentication (spec 01 R30, `app/SharedWebConfig.java:54`). R8 reflects this.
- **Q2 Body validation vs quota.** The quota is checked after body validation, inside the create use case, just before the URL policy (R8, AC13). A malformed request over quota gets `400 validation-failed`.
- **Q3 Invalid `LINK_DAILY_QUOTA`.** Whole number of at least 1, otherwise startup fails (R11, AC19). There is no "0 means unlimited".
- **Q4 `compose.yaml`.** The agent adds `LINK_DAILY_QUOTA: ${LINK_DAILY_QUOTA:-500}` next to `LINK_CREATE_LIMIT_PER_MINUTE` (R12). The engineer adds the `.env.example` line.
- **Q5 Testcontainers.** Allowed. The full gate (`./gradlew check`) runs with Testcontainers; the engineer's Compose containers are never started, stopped or rebuilt.
- **Q6 SECURITY.md.** The engineer edits it. The agent proposes the T9 status text at the end of the spec.
- **Q7 Folder name.** `specs/02-daily-link-quota`, matching AGENTS.md §3 and ARCHITECTURE §11.

`.env.example` line for the engineer to add:
```
# Optional. Links each API key may create per UTC day (default 500).
LINK_DAILY_QUOTA=500
```

## Proposed SECURITY.md text (Q6, for the engineer to apply)
T9 row, Status column. Replace:
> Per-minute creation limit implemented (`CreationRateLimitIT`); daily link quota planned (spec 02); per-IP is a follow-up (D16)

with:
> Per-minute creation limit implemented (`CreationRateLimitIT`); daily link quota implemented (`DailyQuotaIT`, `LinkServiceTest`, `JdbcLinkRepositoryIT`); per-IP is a follow-up (D16)

Optional, for accuracy: S-09 ends "Bucket4j. Exceeding returns `429` …". Bucket4j applies only to the per-minute limit. A possible wording: "The per-minute limit uses Bucket4j; the daily quota is counted from stored links, so it holds across restarts and instances. Exceeding either returns `429` (`rate-limited` or `quota-exceeded`) with `Retry-After` and writes a `RATE_LIMITED` audit event (`CREATE_LIMIT` or `DAILY_QUOTA`). Limitation: in-memory per-minute buckets are per instance (§10); the daily quota may overshoot by one or two under concurrent requests (spec 02 L1)."

## Follow-ups (not in spec 02)
- Per-tier or per-key quotas.
- `X-Quota-Remaining` / `RateLimit` headers or a quota endpoint.
- Monthly quotas.
- Strict quota under concurrency (per-key counter row with `SELECT ... FOR UPDATE`), if L1 ever matters.
- Docs drift found during spec 02, not fixed here: ARCHITECTURE.md §2, §3 and §8 still say Redis arrives "from spec 02", and SECURITY.md §1 lists Redis as a dependency. Both are stale since D16 deferred the cache.
- `CREATE INDEX CONCURRENTLY` in a non-transactional migration, for when `link.links` is large (plan §7).
