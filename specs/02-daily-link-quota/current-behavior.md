# Current behavior: link creation and its limits (`v1-baseline`)

Baseline: tag `v1-baseline` (`4115d00`). Since then only D16 changed `src`: the per-minute default went from 60 to 30 (`f1e1e3f`, `app/LinkConfig.java`, `application.yaml`). Everything below is read from `HEAD` (`9816ac1`).

Paths are relative to `src/main/java/io/github/harshmittal/urlshortener/` unless stated.

## Entry points and data flow

`POST /api/links` passes through these steps, in this order. Each step can end the request.

| # | Step | Where | Rejection |
|---|---|---|---|
| 1 | Request ID filter | `app/SharedWebConfig.java:37-44` | none |
| 2 | Security headers filter | `app/SharedWebConfig.java:46-52` | none |
| 3 | Body cap, 8 KB, **before authentication** | `app/SharedWebConfig.java:54-61`, `shared/web/RequestBodyLimitFilter.java:43-55` | `413 payload-too-large`, not audited, not counted |
| 4 | API-key authentication and role (`OWNER`) | `app/SecurityConfig.java:45-58` | `401 unauthorized` / `403 forbidden`, audited |
| 5 | Per-minute creation limit (MVC interceptor, before body binding) | `app/WebConfig.java:19-22`, `link/adapter/in/web/CreationRateLimitInterceptor.java:43-56` | `429 rate-limited`, audited `RATE_LIMITED` / `CREATE_LIMIT` |
| 6 | Body binding and Bean Validation (`@NotNull targetUrl`) | `link/adapter/in/web/LinkController.java:69`, `:118-119` | `400 validation-failed`, not audited |
| 7 | URL policy | `link/domain/LinkService.java:60-72` | `400 url-rejected`, audited `URL_REJECTED` |
| 8 | Insert link + `LINK_CREATED` in one transaction, code retried up to 4 attempts | `link/domain/LinkService.java:73`, `:129-148` | `503 code-generation-failed` |
| 9 | `201` with `Location`, `{code, shortUrl, targetUrl, createdAt}` | `link/adapter/in/web/LinkController.java:70-74` | — |

Note: the body cap (step 3) runs **before** authentication today. This is spec 01 R30 ("oversized bodies are refused before authentication"), see the comment at `app/SharedWebConfig.java:54`.

### Persistence
- Table `link.links` (`src/main/resources/db/migration/V2__link_links.sql:1-11`): `id`, `code`, `target_url`, `owner_key_id`, `status` (`ACTIVE` | `DELETED`), `created_at timestamptz`.
- Indexes: only the primary key (`id`) and `links_code_uk` (`code`). **No index on `owner_key_id` or `created_at`**, so counting a key's links today would be a sequential scan.
- The app role has `SELECT, INSERT` and `UPDATE (status)` on `link.links` (`V2__link_links.sql:12-14`). No delete path.
- Delete is a soft delete: `UPDATE ... SET status = 'DELETED'`; the row and its `created_at` remain (`link/adapter/out/persistence/JdbcLinkRepository.java:56-62`).
- `created_at` comes from the injected `Clock`, truncated to microseconds (`link/domain/LinkService.java:137`). The `Clock` bean is `Clock.systemUTC()` (`app/SharedWebConfig.java:27-30`).
- `LinkRepository` has `insertIfCodeFree`, `findByCode`, `markDeleted`; no count method (`link/domain/LinkRepository.java`).
- Migrations applied: `V1__identity_api_keys.sql`, `V2__link_links.sql`, `V3__audit_audit_events.sql`. Next free version: `V4`.

### Per-minute limit (spec 01 R15, R16)
- `Bucket4jRateLimiter`, one in-memory bucket per key ID, `capacity = limit`, refilled intervally once a minute (`shared/ratelimit/adapter/out/bucket4j/Bucket4jRateLimiter.java:46-51`). Per instance (spec 01 L3).
- Every authenticated `POST /api/links` consumes a token before the body is read, so attempts later rejected by validation or the URL policy count (`CreationRateLimitInterceptor.java:18-23`).
- Over the limit: WARN log `Rate limited: reason=CREATE_LIMIT actorKeyId=<id>`, audit `RATE_LIMITED`, outcome `REJECTED`, `resource_type = LINK`, `resource_id = null`, `reason_code = CREATE_LIMIT` (`CreationRateLimitInterceptor.java:51-53`). The audit write is best effort in its own transaction (`shared/audit/domain/AuditTrail.java:39-53`).
- Response: `429`, `application/problem+json`, `code = rate-limited`, `type = urn:url-shortener:problem:rate-limited`, `Retry-After` in whole seconds rounded up, minimum 1 (`link/adapter/in/web/LinkExceptionHandler.java:55-62`, `CreationRateLimitedException.java:15-18`).
- Configuration: `url-shortener.link-create-limit-per-minute: ${LINK_CREATE_LIMIT_PER_MINUTE:30}` (`src/main/resources/application.yaml:46-47`); `compose.yaml:63` passes `LINK_CREATE_LIMIT_PER_MINUTE` to the app container with default 30. A value below 1 fails startup (`Bucket4jRateLimiter.java:29-31`); a non-integer fails Spring's property conversion.
- OpenAPI: `POST /api/links` documents one `429` response, "Creation limit reached", with `Retry-After` (`api/openapi.yaml:63-73`, `LinkController.java:60-67`).

## Observed behavior (with evidence)
- **No total or daily cap exists.** Nothing counts a key's links over any period longer than a minute. At the default of 30 per minute a single key can create up to 43,200 links per UTC day. Evidence: no count query in `JdbcLinkRepository`, no other interceptor registered in `WebConfig`, no other `429` source in `LinkExceptionHandler`.
- **Deleted links leave their row in place**, so a count by `owner_key_id` and `created_at` sees them without extra work (`JdbcLinkRepository.java:56-62`; contract test `AC19, R10: markDeleted soft-deletes an active link; the row and its code remain`).
- **Rejected attempts store no row**: URL-policy rejection throws before the transaction (`LinkService.java:60-72`); a code-generation failure rolls back (`LinkService.java:147`); `413`, `401`, `403`, `429` and `400 validation-failed` never reach the service.
- Unit suite run on `HEAD` today: `./gradlew test --rerun` → **326 tests, 0 failures, 0 skipped**. Integration tests (`./gradlew integrationTest`, part of `./gradlew check`) were **not run** for this document; they need Testcontainers (allowed from Gate 1, spec Q5).

## Existing test coverage and gaps

Covered today (these must keep passing unchanged):

| Behavior | Test |
|---|---|
| Create returns `201`, body, `Location`, `LINK_CREATED` | `CreateLinkIT` AC8 (two tests), `LinkServiceTest` AC8 |
| Same target twice gives two codes | `CreateLinkIT` / `LinkServiceTest` AC9 |
| URL-policy rejection: `400`, `URL_REJECTED`, nothing stored | `CreateLinkIT` AC12, `LinkServiceTest` AC12, `UrlRejectedIT` |
| Missing or malformed body: `400 validation-failed` | `CreateLinkIT` R28 |
| Per-minute `429`: code, type, `requestId`, `Retry-After` 1–60, no link stored, audit row | `CreationRateLimitIT` AC26 |
| Configured per-minute limit applies | `CreationRateLimitIT` AC27 |
| Limits are per key | `CreationRateLimitIT` AC28 |
| Validation and URL-policy rejections count toward the per-minute limit | `CreationRateLimitIT` AC29 |
| `413` does not count toward the per-minute limit | `CreationRateLimitIT` R15 |
| Bucket refill and `Retry-After` at the unit level | `Bucket4jRateLimiterTest`, `RateLimiterContract` |
| Rejection still answered when its audit write fails | `RejectionAuditFailureIT` |
| `429` documented in the contract | `ApiContractIT` |

Gaps that characterization tests will close before any code changes (spec Delta, "must stay the same"):
- **G1** The per-minute `429` body's `status` (429) and `title` ("Too Many Requests") and the absence of extra properties (for example `reason`) are not asserted. The new `quota-exceeded` body must not change the `rate-limited` one. Observed in T1: the body also carries `instance = /api/links`, which Spring MVC adds to a `ProblemDetail` without one.
- **G2** No test shows that a key which has created links and deleted some of them can keep creating (today: no cap at all). After the change this holds only below the quota; the characterization fixes today's `201` path for a key with deleted links.
- **G3** ~~A `rate-limited` rejection writes no `LINK_CREATED` row and no link.~~ Already covered on closer reading: AC26 asserts the link count and `singleElement()` on the request's audit rows (`RATE_LIMITED`), and AC29 shows the limit is checked before validation. No new test (plan §8).
- **G4** No integration test sends a successful create at an injected time. All ITs use the real `Clock.systemUTC()`. The quota tests need a controllable clock at the day boundary; that is a plan concern (test-only `Clock` bean), noted here because it touches test support.

## Reproduction steps
Not a bug fix. To exercise today's behavior: issue an owner key (`POST /api/keys` with the admin key), then `POST /api/links` 31 times within a minute: attempts 1–30 get `201`, attempt 31 gets `429 rate-limited`. After the minute, creation succeeds again with no total cap.
