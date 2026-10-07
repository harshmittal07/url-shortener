# Tasks: 02 Daily link quota per API key
Status: Approved (combined plan and tasks gate, engineer, 2026-10-07)
Each task is one atomic commit, made only after the engineer's go-ahead. Tests come before the code they cover. Gate per task: `./gradlew spotlessApply`, `./gradlew check` (real output reported), `gitleaks git --staged`, and an AI log row.

- [x] **T1 Characterization of today's create and 429** (AC21, AC22; gaps G1, G2)
  - Done. The baseline `./gradlew check --rerun-tasks` on unchanged code ran 326 unit and 168 integration tests with 0 failures. After T1: 326 unit and 170 integration, 0 failures. AC22's first run showed that Spring adds `instance = /api/links`; it is now pinned, and the spec's AC8 and AC22 list it.
  - Before writing anything, run `./gradlew check` on unchanged code and record the baseline result.
  - `CreationRateLimitIT`: + "AC22 (spec 02): the rate-limited body keeps its exact field set". Pins every JSON field of today's `429 rate-limited` body, including `title = Too Many Requests` and `status = 429`, and that there is no `detail` and no `reason`.
  - `CreateLinkIT`: + "G2 (spec 02): a key that created and deleted links can create again" (`201`).
  - These tests must pass on today's code. No production file changes in this task.
  - Note in `current-behavior.md` that G3 was already covered by AC26 (done at this gate).
  - Commit: `test(link): characterize link creation and the rate-limited 429 before the daily quota`.

- [x] **T2 Daily link quota with V4** (AC1–AC20, AC21 re-run; R1–R14). High-impact (migration, rate limiting, `app/`), covered by this plan.
  - Done, test-first, with each step shown red before green:
    - repository: 7 red (3 contract tests × 2 adapters, plus `SchemaIT`)
    - use case: 6 red (the 3 "allowed" cases passed already, as expected)
    - configuration: 12 red; the placeholder's `NumberFormatException` echoed the value
    - end to end: 9 red (`500 internal-error` before the handler)
  - Gate: `./gradlew check` green, 354 unit and 186 integration tests, 0 failures. Domain line coverage 95.5% (450/471). `ApiContractIT` (oasdiff) green. V1–V3 are identical to `v1-baseline`.
  - Deviation: the integration test profile now caps the Hikari pool at 5, because the shared test Postgres ran out of connections once `DailyQuotaIT` added two contexts (plan §9). `DailyQuotaDefaultIT` is in `app`, not `link`.
  1. **Repository count, red then green:** `LinkRepositoryContract` + "R1: count window is [from, until)", "R2: count includes deleted links", "R1: count is per owner". Then `countCreatedBy` on the port, `InMemoryLinkRepository` and `JdbcLinkRepository`. Then `V4__link_links_owner_created_index.sql` and `SchemaIT` "AC20: index exists".
  2. **Use case, red then green:** `LinkServiceTest` quota tests for AC1, AC2, AC3, AC5, AC6, AC7, AC9 and AC11. The fixture passes `Integer.MAX_VALUE` to existing tests. Then `DailyQuotaExceededException` and `LinkService.requireWithinDailyQuota`.
  3. **Configuration, red then green:** `DailyQuotaSettingTest` (AC19). Then `DailyQuotaSetting`, `LinkConfig`, `application.yaml` and `compose.yaml` (R12).
  4. **End to end, red then green:** `SettableClock.set(Instant)`, `DailyQuotaIT` (AC1, AC3, AC4, AC6, AC7, AC8, AC10, AC12–AC16, AC18) and `DailyQuotaDefaultIT` (AC17). Then the `LinkExceptionHandler` `429 quota-exceeded` handler and the `429` description in `LinkController` and `api/openapi.yaml`.
  5. **Docs:** `ARCHITECTURE.md` (§6 create-link flow, §9 failure-mode row). In the spec, the traceability table and the proposed SECURITY.md T9 text.
  6. **Gate:** `./gradlew check` green, with real output reported. `git diff --exit-code v1-baseline -- src/main/resources/db/migration/V1__identity_api_keys.sql src/main/resources/db/migration/V2__link_links.sql src/main/resources/db/migration/V3__audit_audit_events.sql` (AC20).
  7. **Mutation check (deleted links):** stage T2. Use the Edit tool to add `AND status = 'ACTIVE'` to the count query in `JdbcLinkRepository`. Run `./gradlew integrationTest --tests '*JdbcLinkRepositoryIT' --tests '*DailyQuotaIT'`. Expected failures: "R2: count includes deleted links" and "AC3, S-09: deleted links still count". Restore with `git checkout -- src/main/java/io/github/harshmittal/urlshortener/link/adapter/out/persistence/JdbcLinkRepository.java`, check with `git diff --exit-code` on the file, then re-run the two classes green. Record the outputs here.
  8. Commit: `feat(link): daily link quota per API key (spec 02)`. The body lists R1–R14 and AC1–AC22.

## Mutation check result
Mutation: `AND status = 'ACTIVE'` added to the count query in `JdbcLinkRepository.countCreatedBy` with the Edit tool, after T2 was staged. SHA-256 of the file before: `4f41b899…eeae4a`.

`./gradlew integrationTest --tests '*JdbcLinkRepositoryIT' --tests '*DailyQuotaIT*'` → `BUILD FAILED`, with exactly the two expected failures:
- `JdbcLinkRepositoryIT` "R2 (spec 02), S-09: the count includes the owner's deleted links": `expected: 2L but was: 1L` (1 of 9 failed)
- `DailyQuotaIT` "AC3 (spec 02), S-09: deleted links still count toward the quota": `Status expected:<429> but was:<201>` (1 of 11 failed)

Restored with `git checkout -- …/JdbcLinkRepository.java`. `git diff --exit-code` showed no difference from the index, and the SHA-256 after matches the one before. The re-run was green: 9 of 9 and 11 of 11.

## Traceability
T1 = `281caca`. T2 = the spec 02 feature commit.

| R | AC | Tests (`@DisplayName` prefix) | Task |
|---|---|---|---|
| R1 | AC1, AC2, AC5, AC6, AC7 | `LinkServiceTest$DailyQuota` AC1, AC2, AC5, AC6/AC9, AC7; `LinkRepositoryContract` "R1 … [from, until)", "R1 … only the given owner's"; `DailyQuotaIT` AC1/AC10, AC6/AC9, AC7 | T2 |
| R2 | AC3 | `LinkServiceTest$DailyQuota` AC3; `LinkRepositoryContract` "R2 … includes the owner's deleted links" (mutation-checked); `DailyQuotaIT` AC3 (mutation-checked) | T2 |
| R3 | AC4, AC16 | `DailyQuotaIT` AC4 (413, url-rejected, validation-failed); `DailyQuotaIT$CheckOrder` AC4 (per-minute), AC12/AC16 | T2 |
| R4 | AC1, AC3 | `JdbcLinkRepositoryIT` (count from `link.links`) | T2 |
| R5 | AC1, AC8 | `LinkServiceTest$DailyQuota` AC1; `DailyQuotaIT` AC1/AC10, AC8 | T2 |
| R6 | AC8, AC9 | `LinkServiceTest$DailyQuota` AC8, AC6/AC9; `DailyQuotaIT` AC8, AC6/AC9 | T2 |
| R7 | AC10, AC11 | `DailyQuotaIT` AC1/AC10; `LinkServiceTest$DailyQuota` AC1, AC11 | T2 |
| R8 | AC12–AC15 | `DailyQuotaIT$CheckOrder` AC12/AC16; `DailyQuotaIT` AC13, AC14, AC15; `LinkServiceTest$DailyQuota` AC14 | T2 |
| R9 | — | Accepted overshoot (L1), documented in the spec, plan §7 and `LinkService` Javadoc; not tested | — |
| R10 | AC5, AC6 | `LinkServiceTest$DailyQuota` AC5, AC6/AC9; `DailyQuotaIT` AC6/AC9 | T2 |
| R11 | AC17, AC18, AC19 | `DailyQuotaDefaultIT` AC17; `DailyQuotaSettingTest` AC18, AC19 (both); `LinkServiceTest` "R11 … below 1 is refused"; `DailyQuotaIT` runs with 3 and 2 | T2 |
| R12 | — | `compose.yaml` line; checked on your next `docker compose up` (not automated) | T2 |
| R13 | AC20 | `SchemaIT` AC20; `git diff --exit-code v1-baseline` on V1–V3; `DatabasePrivilegesIT` unchanged and green | T2 |
| R14 | — | WARN line in `LinkService.requireWithinDailyQuota`; follows the per-minute line's shape. Log content is not test-enforced (D15 moved log-level tests to a follow-up); `LogHygieneIT` still green | T2 |
| Delta | AC21, AC22 | Every spec 01 test unchanged and green; `CreationRateLimitIT` AC22; `CreateLinkIT` G2 | T1, T2 |

Requirements without an automated test: R9 (accepted race), R12 (Compose wiring), R14 (log line). Each is listed above with how it is covered instead.
