# Tasks: 03 List links
Approved with spec and plan in one gate (engineer, 2026-10-07, D16 amendment). Built in one run; one commit at the end.

- [x] T1 (AC1–AC6, AC10, R4 guard): Domain and persistence, test-first.
  - Red: `LinkRepositoryContract` (+4: owner scoping and empty, active only, order with binary tie-break, limit) on both adapters; `LinkServiceTest.ListLinks` (+5 cases: returns the port's links, rejects limit 0, -1 and 101, writes no audit event). Port method and `LinkService.list` added as throwing stubs so red is behavioural. Seen red: 9 unit (in-memory contract and service), 4 JDBC contract.
  - Green: `InMemoryLinkRepository`, `JdbcLinkRepository.findActiveByOwner`, `LinkService.list`.
- [x] T2 (AC1–AC12): Web and contract, test-first.
  - Red: new `ListLinksIT` (14 cases, all red: no `GET` mapping); `AuthenticationIT` endpoint list gains `GET /api/links` (6 cases, green before any code: security runs before routing, so they characterize existing behaviour).
  - Green: `LinkController.list` and `LinkListResponse`; `get` reuses the new `toResponse`; `api/openapi.yaml` (additive).
  - Mutation check: see below.
- [x] T3: Docs and gate. ARCHITECTURE §5 row; AI log rows; `./gradlew spotlessApply`; `./gradlew check`; gitleaks on tracked files and `src`; summary to the engineer; one commit after "approved".

## Mutation check result
Removed `owner_key_id = :ownerKeyId AND` from the `findActiveByOwner` query in `JdbcLinkRepository`.
- Red: 10 of 27 tests in `ListLinksIT` and `JdbcLinkRepositoryIT` failed, including both cross-owner tests: `ListLinksIT` AC2 and `JdbcLinkRepositoryIT` AC2 (S-08). The other failures saw other keys' links too (AC1, AC4–AC6, AC11; contract AC3–AC6).
- `ListLinksIT` AC7, AC8, AC10 and AC12 stayed green as expected: they don't assert which links are listed. AC3 also stayed green; it asserts the exact list, so it most likely ran before other active links existed in the shared database (not investigated). AC2 is the test that guards S-08.
- Restored with the edit tool; SHA-256 before and after `2677b27a…c396668` (match); 27 of 27 green again.

## Traceability
| R | AC | Tests | Task |
|---|---|---|---|
| R1 | AC1 | `ListLinksIT` AC1; `LinkServiceTest.ListLinks` AC1 | T1, T2 |
| R2 | AC2, AC3 | `ListLinksIT` AC2, AC3; `LinkRepositoryContract` AC2, AC3 (both adapters) | T1, T2 |
| R3 | AC4 | `ListLinksIT` AC4; `LinkRepositoryContract` AC4 | T1, T2 |
| R4 | AC5, AC6 | `ListLinksIT` AC5, AC6; `LinkRepositoryContract` AC5/AC6; `LinkServiceTest` R4 guard | T1, T2 |
| R5 | AC7, AC8 | `ListLinksIT` AC7 (4 cases), AC8 | T2 |
| R6 | AC9 | `AuthenticationIT` AC3 (5 bad credentials) and AC5 (admin) on `GET /api/links` | T2 |
| R7 | AC10 | `ListLinksIT` AC10; `LinkServiceTest` AC10; `ListLinksIT` AC7 (no row on 400) | T1, T2 |
| R8 | AC11 | `ListLinksIT` AC11 | T2 |
| R9 | AC12 | `ListLinksIT` AC12; `ApiContractIT` AC48 | T2 |
