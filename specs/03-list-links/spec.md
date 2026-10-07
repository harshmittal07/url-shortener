# 03 List links
Mode: A (problem first, ambiguous requirement)
Status: Approved (engineer, 2026-10-07; spec, plan and tasks in one gate per the D16 amendment)
Decision: D16. Controls: S-08 (owner scoping), S-10 (audit), S-12 (log hygiene), S-16 (safe errors).
Clarification log: `clarifications.md` (Q1–Q11, answered 2026-10-07).

## Problem
"Marketing teams want to see their links." Today an owner can read one link only if they already know its code (`GET /api/links/{code}`). A team that has lost track of its codes has no way to find its links again. D16 asks for a narrow list endpoint, `GET /api/links`.

## Goals
- G1: An owner key can list its own active links, newest first, with the same fields as a single-link read.
- G2: No key can ever see, count or infer another key's links (S-08).
- G3: The response can grow (paging cursor, total count) without breaking clients (D6).

## Non-goals
- Teams that group several keys (Q1). A team is one API key.
- Deleted links in the list, or a status filter (Q2).
- Click counts (D16 defers click analytics).
- Cursor or offset paging (Q4), search, date-range filters (Q7), sorting options (Q6), total count (Q8).
- An admin view of every key's links (Q1 C).
- A rate limit on listing (Q11).
- Any UI or export format.

## Requirements
- R1: `GET /api/links` with an owner key shall return `200`, `application/json`, body `{ "items": [ ... ] }`. Each item has the `Link` schema of `GET /api/links/{code}`: `code`, `shortUrl`, `targetUrl`, `status`, `createdAt` (Q3, Q5).
- R2: `items` shall hold only links whose owner is the calling key and whose status is `ACTIVE`. Links of other keys and deleted links never appear. A key with no active links gets `{ "items": [] }` (Q1, Q2, S-08).
- R3: Items are ordered by `createdAt` descending, then by `code` descending in binary (case-sensitive ASCII) order, so the order is total (Q6).
- R4: Optional query parameter `limit`: a whole number from 1 to 100, default 50. The response holds at most `limit` items: the first `limit` links in R3 order. There is no cursor; only the newest 100 active links can be listed (Q4, L2).
- R5: A `limit` that is outside 1–100 or not a whole number shall be refused with `400`, `application/problem+json`, `code = validation-failed`, with the standard problem fields and no internal detail (S-16). Unknown query parameters, including `cursor`, are ignored (Q10, AGENTS.md §7).
- R6: Authentication and role are unchanged: no key or a bad key gets `401` (audited `AUTH_FAILED`); the admin key gets `403` (audited `ACCESS_DENIED`). These come from the existing security handlers (S-07, S-08).
- R7: A successful list and a `400` write no audit row. No new audit action is added (Q9, S-10).
- R8: A successful list logs one info line with the caller's key ID and the number of items. It never logs target URLs, the query string or the API key (Q9, S-12).
- R9: The contract change is additive: a new `GET` operation on `/api/links` (`listLinks`) and a new `LinkList` schema with a required `items` array of `Link`. `api/openapi.yaml` is updated in the same commit and the oasdiff check passes (D6).

## Acceptance criteria
- AC1 (R1): Given an owner with two active links, when it calls `GET /api/links`, then it gets `200` JSON with both links, each with `code`, `shortUrl`, `targetUrl`, `status = ACTIVE` and `createdAt` matching the stored link.
- AC2 (R2, S-08): Given two owners with links each, when each lists, then each sees only its own links; given an owner with no links, then it gets `{ "items": [] }`.
- AC3 (R2): Given an owner with an active and a deleted link, when it lists, then only the active link appears.
- AC4 (R3): Given links with different creation times and two links with the same creation time, when the owner lists, then items are newest first and the tie is ordered by `code` descending in binary order (`a…` before `B…`).
- AC5 (R4): Given an owner with 51 active links, when it lists without `limit`, then it gets the 50 newest.
- AC6 (R4): Given an owner with 101 active links, when it lists with `limit=100`, then it gets the 100 newest; with `limit=1`, the newest one.
- AC7 (R5, S-16): When the owner lists with `limit` `0`, `101`, `-1` or `abc`, then it gets `400` problem+json with `code = validation-failed` and its `requestId`, and no audit row is written.
- AC8 (R5): When the owner lists with unknown parameters (`cursor`, `status=DELETED`), then the response equals the response without them.
- AC9 (R6): `GET /api/links` with no key, a malformed, unknown or revoked key gets `401` with `AUTH_FAILED`; with the admin key it gets `403` with `ACCESS_DENIED`.
- AC10 (R7): A successful list writes no audit row.
- AC11 (R8, S-12): A successful list logs a "Links listed" line with the key ID and count, and the output contains no target URL of the listed links.
- AC12 (R9): The generated OpenAPI document has `GET /api/links` (`listLinks`) with an integer `limit` (minimum 1, maximum 100, default 50) and a `200` `LinkList` response whose required `items` is an array of `Link`; the oasdiff check (AC48) passes against the updated `api/openapi.yaml`.

## Edge cases and error handling
- An empty `limit=` is treated as absent (default 50), as Spring does for parameters with a default.
- A repeated `limit` (`limit=2&limit=3`) takes the first value (Spring binding); not specified further.
- A link deleted between two calls simply disappears from the next list.
- Listing does not count against the per-minute creation limit (the interceptor counts only `POST`, `CreationRateLimitInterceptor.java:44`) or the daily quota (which counts created links).
- Database unavailable: `503 service-unavailable` from the shared handler, as on every endpoint.

## Non-functional requirements
- Security: S-08 owner scoping is enforced in the query itself (owner condition in SQL), not by filtering after the read. Cross-owner test plus a mutation check that removes the owner condition.
- Performance: one indexed query per request, bounded by `limit ≤ 100`, served by `links_owner_created_idx` (V4). No schema change.
- Observability: R8 log line; no metrics added.
- Compatibility: additive only (D6); the `{ items }` envelope leaves room for `nextCursor` and `total`.

## Known limitations
- L1: Key rotation hides the old key's links from the new key's list. They still redirect and the old key's links stay in the database (Q1).
- L2: Only the newest 100 active links of a key can be listed. Older ones are reachable only by code (Q4).
- L3: No search or filter (Q7).
- L4: Listing has no rate limit of its own; each request is a bounded indexed read (Q11, T9).

## Follow-ups (not in spec 03)
- Cursor paging: an opaque `cursor` parameter and `nextCursor` in the envelope, keyset on (`createdAt`, `code`) (Q4).
- Teams grouping several keys, with a list across the team (Q1 B).
- Status filter including deleted links (Q2 C).
- Click counts per item once click analytics exists (D16).
- Date range and target-URL search (Q7). Total count (Q8). Per-key read rate limit (Q11).
