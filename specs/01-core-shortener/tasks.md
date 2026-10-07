# Tasks: 01 Core shortener
Spec: `specs/01-core-shortener/spec.md` (Approved). Plan: `specs/01-core-shortener/plan.md` (Approved).
Status: Approved (Gate 3)

Each task is one commit, written test first, and ends with `./gradlew check` green (real output reported), Spotless and gitleaks clean, and a drafted `docs/AI_LOG.md` row. Boot 4 compatibility of each new dependency or plugin is checked first in the task that adds it (plan §10). One task at a time; the next starts only after the engineer's go-ahead.

- [x] **T1 Foundation and gate** (AC6, AC7, AC25 (arch), AC31, AC34, AC41)
  - Move the main class to `app`; create the package skeleton (plan §3).
  - Swap JPA for `spring-boot-starter-jdbc` (D14).
  - `docker/postgres/initdb/01-users.sh`; Flyway migrations V1–V3 with grants to the app user (plan §5).
  - Testcontainers Postgres 16 with Flyway as the migration user; `test` / `integrationTest` split (plan §11).
  - Request-ID filter (A5) and the shared problem+json handler, including `500 internal-error`.
  - ArchUnit rules 1–6, Spotless, JaCoCo 80% on `domain` packages. Check Spotless on Gradle 9.7 and Boot 4.1 first.
  - Tests first: `MigrationsContainNoSecretsTest`, `SchemaIT`, `DatabasePrivilegesIT`, `RequestIdIT`, ArchUnit tests.

- [x] **T2 Vertical slice: keys, create, redirect, audit** (AC1–AC5, AC8–AC11, AC22–AC25, AC30 (create), AC32)
  - Identity: `ApiKeyIssuer`, `ApiKeyAuthenticator` (dummy-hash compare on miss), `ApiKeyAuthenticationFilter`, roles `ADMIN` / `OWNER`, `POST /api/keys`, `scripts/new-admin-key.sh` (plan §6).
  - Audit: `AuditTrail` with the change path (same transaction) and the rejection path (`requiresNew`, best effort); `JdbcAuditSink`; `UnitOfWork`.
  - Link: `LinkService.create` with `SecureRandomShortCodeGenerator`, `ON CONFLICT DO NOTHING` and up to 3 retries; `StandardUrlPolicy` with the scheme rule only; `POST /api/links`.
  - Redirect: `RedirectService` through `link.api.LinkLookup`; `GET /{code}`.
  - Port contract tests for every port added (plan §3).
  - Tests first: `KeyIssuanceIT`, `AuthenticationIT`, `LinkServiceTest`, `CreateLinkIT`, `RedirectServiceTest`, `RedirectIT`, `AuditAtomicityIT` (create), `AuditEventShapeIT`.
  - If T2 overruns, split at "keys and auth" / "create and redirect" and tell the engineer before continuing (plan §14).

- [ ] **T3 Owner-scoped read and delete** (AC18–AC21, AC30 (delete))
  - `GET` and `DELETE /api/links/{code}`; soft delete to `DELETED`; cross-owner access returns the same `404` as an unknown code and writes `ACCESS_DENIED`.
  - Tests first: `LinkServiceTest` (get, delete), `ManageLinkIT` (byte-for-byte `404` comparison), `AuditAtomicityIT` (delete).

- [ ] **T4 URL policy** (AC12–AC17)
  - Complete S-01 to S-05 in `StandardUrlPolicy`: host ranges and IP encodings, userinfo, self-reference, malformed and too long, IDN to punycode.
  - Tests first: `StandardUrlPolicyTest` (a parameterized table per reason, plus punycode), `UrlRejectedIT`.

- [ ] **T5 Abuse controls and hardening** (AC26–AC29, AC33, AC40, AC42–AC45)
  - `RateLimiter` port with `Bucket4jRateLimiter` (Clock-driven) and `CreationRateLimitInterceptor`. Check Bucket4j on Boot 4.1 first.
  - `RequestBodyLimitFilter` (8 KB, declared and streamed); security headers; database-outage `503` with a 2 s Hikari timeout; rejection-audit failure handling; log hygiene; actuator on the management port with `health` and `info` only.
  - Tests first: `RateLimiterContract`, `CreationRateLimitIT`, `RejectionAuditFailureIT`, `LogHygieneIT`, `ErrorResponsesIT`, `SecurityHeadersIT`, `BodyLimitIT`, `ActuatorExposureIT`.

- [ ] **T6 Packaging and contract** (AC35, AC36, AC47 (M), AC48)
  - `RequiredEnvironmentCheck`; Dockerfile (distroless, non-root); Compose with the users init script, one-shot Flyway step, `read_only` and `tmpfs`.
  - springdoc and the committed `api/openapi.yaml`; oasdiff drift and breaking checks with `extractApiBaseline` and `-PapiBaselineRef`. Check springdoc 3.x and the oasdiff image on Boot 4.1 first; raise any fallback with the engineer before using it (plan §10).
  - `scripts/smoke-test.sh`.
  - `.env.example`: the engineer adds the variables from plan §9. The agent deny rule stays.
  - Tests first: `RequiredEnvironmentCheckTest`, `ApiContractIT` (with a breaking-change fixture).

- [ ] **T7 Polish** (AC37–AC39, AC46)
  - ECS structured logs and R23 levels; `MirroringAuditSink` decorator with the `AUDIT` logger and after-commit mirroring; Swagger UI in the `local` profile.
  - Tests first: `LoggingIT`, `OpenApiExposureIT`.
  - Can become a follow-up if time runs short. No security control depends on it.
