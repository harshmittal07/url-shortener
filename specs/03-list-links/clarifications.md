# 03 List links: clarification log
Mode: A (problem first, ambiguous requirement)
Status: Resolved (engineer, 2026-10-07; see Answers)
Decision: D16 (spec 03 is the ambiguous scenario; this log is the main deliverable).

## The request, as given
> "Marketing teams want to see their links."

Five words, and almost every one is ambiguous. "Marketing teams" names a group of people the system has no concept of. "See" could mean an API, a dashboard or an export. "Their" depends on what owns a link. "Links" could mean active links only, or also deleted ones, and with or without click numbers.

## Already fixed by existing decisions (not asked)
These follow from locked documents. Changing any of them needs a new decision.

| Topic | Fixed answer | Source |
|---|---|---|
| Endpoint | `GET /api/links` | D16, ARCHITECTURE §5 |
| Who may call it | The `OWNER` role. The admin key gets `403`, as on every other link endpoint. `SecurityConfig` already maps `/api/links` to `OWNER`, so no security change is needed | S-08, `app/SecurityConfig.java` |
| Owner isolation | Only the caller's links ever appear. Another key's links are never listed, counted or hinted at | S-08, AGENTS.md §7 |
| No UI | JSON API only. A login UI is out of scope | ARCHITECTURE §1 |
| Contract evolution | Additive only. The response must be shaped so it can grow without breaking clients | D6, AGENTS.md §7 |
| Click counts | Not available: click analytics is deferred, so no click data exists | D16 |
| Errors | RFC 9457 problem details, stable codes, no internals | S-16 |

## Open questions
Each question has options and a recommendation. Answer with the option letter, or "as recommended", or your own text.

### Q1 What is a "team"?
The system has no team concept. An owner is one API key (`links.owner_key_id`), and keys carry no name or group.

- **A. Team = API key (recommended).** "Their links" means the links created with the calling key. A team that shares one key sees all its links. No schema or identity change.
- B. Add a team entity that groups several keys, and list every link created by any key in the caller's team. Needs an identity schema change, a way to assign keys to teams (admin endpoint), a new decision on ownership, and changes to S-08. Several hours of work.
- C. Let the admin key list everyone's links. Needs an admin variant of the endpoint and its own audit rules. Does not answer "their links".

Why A: it is the only option inside the current design. B is a real product need if teams run several keys, so I'd record it as a follow-up.
Consequence of A to accept: when a team rotates its key (revoke and reissue), the old key's links disappear from the new key's list. They still redirect. This becomes a known limitation (L1).

### Q2 Which links: active only, or deleted too?
- **A. Active only (recommended).** Deleted links already answer `404` on `GET /api/links/{code}` and on redirect (spec 01 R11). Listing them would be the only place they are still visible.
- B. Active and deleted, each with its `status`.
- C. Active by default, deleted on request with `?status=DELETED`.

Why A: it is consistent with every other endpoint, and a deleted link cannot be acted on. C can be added later without breaking anything.

### Q3 What does each item show?
- **A. The same fields as `GET /api/links/{code}` (recommended):** `code`, `shortUrl`, `targetUrl`, `status`, `createdAt`, reusing the existing `Link` schema.
- B. A slimmer item (`code`, `shortUrl`, `createdAt`) without the target URL.
- C. A or B plus a click count. Not possible: no click data exists (D16).

Why A: one schema for one resource, and marketing needs the destination to recognise a link. The target URL is the caller's own data; returning it is fine. It is still never logged (S-12).

### Q4 How are results paged?
At the default quota (500 per day) one key can hold about 180,000 links a year, so an unpaged list is not an option.

- **A. Cursor (keyset) paging (recommended).** Query parameters `limit` (default 50, maximum 100) and `cursor` (opaque). The response returns `nextCursor`, absent on the last page. Stable while new links are being created, constant cost per page, and served by the existing `links_owner_created_idx` index (V4) with no migration.
- B. Offset paging (`page`, `size`). Simpler to read, but pages shift when links are created or deleted between calls, and deep pages get slower.
- C. No paging, with a hard cap (for example the newest 1,000 links). Silently hides older links.

Why A: correct under concurrent creation, cheap at any depth, and needs no schema change.
The cursor encodes the position only, never the owner. A tampered cursor can only move the caller within their own links; a malformed one gets `400 validation-failed`.

### Q5 What is the response shape?
- **A. An envelope object (recommended):** `{ "items": [ ...Link ], "nextCursor": "..." }`.
- B. A bare JSON array with paging in a `Link` response header.

Why A: an object can gain fields later (for example a total count) without breaking clients (D6). A bare array cannot.

### Q6 In what order?
- **A. Newest first (recommended):** `createdAt` descending, then `code` descending as a tie-break so the order is total and the cursor is exact.
- B. Oldest first.
- C. Client-chosen sort (`?sort=`).

Why A: marketing most often wants what they just made. C is a follow-up if asked for.

### Q7 Filtering or search?
Marketing may want to find a campaign's links, by target domain, by date range or by text.

- **A. None in this spec (recommended).** Record target-URL search, date range and status filters as follow-ups.
- B. A creation date range (`createdFrom`, `createdTo`).
- C. A target-URL substring search. Needs a trigram index (new migration) to stay fast, and raises log-hygiene questions for the search term (S-12).

Why A: D16 asks for a narrow list endpoint. Any of these can be added later as optional parameters without breaking clients.

### Q8 Total count?
- **A. No total (recommended).** Counting all of a key's links on every page is extra cost and isn't needed to browse.
- B. Include `total` in the envelope.

Why A: it can be added later to the envelope (Q5 A) if someone asks.

### Q9 Audit and logging for a read?
Reads change no state. Today a successful `GET /api/links/{code}` writes no audit row; `401` and `403` are audited by the security handlers; `400 validation-failed` is not audited anywhere.

- **A. Same as today (recommended).** No audit row for a successful list or for a `400`. `401`/`403` stay audited by the existing handlers. One info log line per request: key ID, item count and whether a next page exists. Never target URLs or the cursor value.
- B. Also write an audit row for every successful list (a new action such as `LINKS_LISTED`). Adds a new audit action to SECURITY.md S-10 and one write per read.

Why A: S-10 audits state changes and security rejections, and a list is neither. Owner isolation is enforced by the query, so there is no cross-owner attempt to record (unlike `GET /api/links/{code}`, where a known code of another key is audited as `ACCESS_DENIED`).

### Q10 Invalid query parameters?
- **A. Reject (recommended):** `limit` outside 1–100, a non-number `limit` or a malformed `cursor` → `400 validation-failed`. Unknown parameters are ignored (tolerant reader, AGENTS.md §7).
- B. Clamp `limit` silently to 1–100, and treat a bad cursor as "first page".

Why A: silent clamping hides client bugs, and treating a bad cursor as the first page could loop a client forever.

### Q11 Rate limiting for the list?
The per-minute limit (S-09) covers creation only.

- **A. No new limit in this spec (recommended).** Each page is bounded (`limit` ≤ 100) and served by an index. Per-key read limits, like the per-IP limit, stay a follow-up.
- B. Apply the per-key per-minute limiter to listing too, sharing or separate from the creation budget.

Why A: a bounded indexed read per request is cheap, and sharing the creation budget would let browsing block link creation. Recorded as a known limitation under T9.

## Proposed known limitations (if the recommendations are accepted)
- L1: Key rotation hides the old key's links from the new key (Q1).
- L2: No search or filter; teams page through newest first (Q7).
- L3: Listing has no rate limit of its own (Q11).

## Proposed follow-ups (not in spec 03)
- Teams that group several keys, with a list across the team (Q1 B).
- Status filter including deleted links (Q2 C).
- Click counts per link, once click analytics exists (D16).
- Date range and target-URL search (Q7).
- Total count (Q8).
- Per-key read rate limit (Q11).

## SECURITY.md impact (for the engineer)
If all recommendations are accepted, I expect no change is needed: S-08 already covers owner scoping for management endpoints, and no new audit action or control is introduced. I'll confirm after the build, and propose text if the tests show otherwise.

## Answers (engineer, 2026-10-07)
- **Q1–Q3, Q5–Q11:** as recommended (option A each).
- **Q4:** changed. `limit` only (default 50, maximum 100), **no cursor for now**. Keep the `{ items }` envelope so `nextCursor` can be added later as a non-breaking change. Cursor paging is a follow-up.

Consequences of the Q4 answer, carried into `spec.md`:
- Q10 loses its cursor clause: only `limit` is validated. A `cursor` parameter sent today is an unknown parameter and is ignored.
- New limitation: only the newest 100 active links of a key can be listed (spec L2). Older links are still readable one by one with `GET /api/links/{code}` and still redirect.
- The proposed limitations and follow-ups above are final in `spec.md`.
