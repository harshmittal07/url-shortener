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

- [ ] **T2 Daily link quota with V4** (AC1–AC20, AC21 re-run; R1–R14). High-impact (migration, rate limiting, `app/`), covered by this plan.
  1. **Repository count, red then green:** `LinkRepositoryContract` + "R1: count window is [from, until)", "R2: count includes deleted links", "R1: count is per owner". Then `countCreatedBy` on the port, `InMemoryLinkRepository` and `JdbcLinkRepository`. Then `V4__link_links_owner_created_index.sql` and `SchemaIT` "AC20: index exists".
  2. **Use case, red then green:** `LinkServiceTest` quota tests for AC1, AC2, AC3, AC5, AC6, AC7, AC9 and AC11. The fixture passes `Integer.MAX_VALUE` to existing tests. Then `DailyQuotaExceededException` and `LinkService.requireWithinDailyQuota`.
  3. **Configuration, red then green:** `DailyQuotaSettingTest` (AC19). Then `DailyQuotaSetting`, `LinkConfig`, `application.yaml` and `compose.yaml` (R12).
  4. **End to end, red then green:** `SettableClock.set(Instant)`, `DailyQuotaIT` (AC1, AC3, AC4, AC6, AC7, AC8, AC10, AC12–AC16, AC18) and `DailyQuotaDefaultIT` (AC17). Then the `LinkExceptionHandler` `429 quota-exceeded` handler and the `429` description in `LinkController` and `api/openapi.yaml`.
  5. **Docs:** `ARCHITECTURE.md` (§6 create-link flow, §9 failure-mode row). In the spec, the traceability table and the proposed SECURITY.md T9 text.
  6. **Gate:** `./gradlew check` green, with real output reported. `git diff --exit-code v1-baseline -- src/main/resources/db/migration/V1__identity_api_keys.sql src/main/resources/db/migration/V2__link_links.sql src/main/resources/db/migration/V3__audit_audit_events.sql` (AC20).
  7. **Mutation check (deleted links):** stage T2. Use the Edit tool to add `AND status = 'ACTIVE'` to the count query in `JdbcLinkRepository`. Run `./gradlew integrationTest --tests '*JdbcLinkRepositoryIT' --tests '*DailyQuotaIT'`. Expected failures: "R2: count includes deleted links" and "AC3, S-09: deleted links still count". Restore with `git checkout -- src/main/java/io/github/harshmittal/urlshortener/link/adapter/out/persistence/JdbcLinkRepository.java`, check with `git diff --exit-code` on the file, then re-run the two classes green. Record the outputs here.
  8. Commit: `feat(link): daily link quota per API key (spec 02)`. The body lists R1–R14 and AC1–AC22.

## Mutation check result
_Filled in during T2._

## Traceability
_Filled in after T2: R → AC → test → task → commit._
