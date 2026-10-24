# Roomify submission — progress log

Running log kept by the lead engineer. Newest entries at the bottom of each section.
Location rationale: `roomify_backend/docs/submission/` — the backend is the integration
point of the three services and already hosts `docs/`, so one place holds the cross-repo package.

## Environment notes (2026-10-24)

- `scope.md` was missing from the workspace; regenerated from the source Google Doc
  ("ROOMIFY-project scope") and saved as `docs/submission/scope.md`.
- No Jira connector is available. ROOM-87..101 were reconstructed from PR titles, branch
  names, commit messages and in-repo docs (see "Backlog mapping"); unknown titles are marked `?`.
- Local ports 5433 and 8080 are used by unrelated projects' containers; Roomify runs on
  Postgres `127.0.0.1:5439` (throwaway `postgres:17` container) and backend `:8081` locally.
- Local runs use `STORAGE_TYPE=local` and blank AWS credentials so tests never touch a real bucket.

## Security

- 2026-10-24 — Secret scan of full history (all refs + unreachable objects) in all three repos
  for AWS key IDs, AWS secrets, OpenAI/Google keys, private keys and credential-like property
  assignments: **no secrets found in git history**. `.env` is gitignored in all three repos;
  only `.env.example` has ever been committed.
- 2026-10-24 — **Flag:** `roomify_backend/.env` (untracked, local only) contains a full AWS access
  key pair and `STORAGE_TYPE=s3`. If this is the key that leaked earlier it must be rotated.
  Not used by any run in this work.

## Phase 1 / Step 1 — open PRs

Baseline before merging: backend develop 35/35 tests, CV 12/12, frontend 195/195 unit +
typecheck + lint + build. No open PRs in roomify_cv or roomify_frontend.

| PR | Ticket | Action | Tests after sync |
|---|---|---|---|
| backend#26 | ROOM-98 recommendation/DIY contracts | merged develop in (clean), merge commit | 63/63 |
| backend#27 | ROOM-101 recommendation persistence | retargeted to develop after #26, merged develop in (clean) | 87/87 |
| backend#25 | ROOM-94 image-generation adapter | merged develop in (clean) | 93/93 |
| backend#24 | ROOM-88/92/96 design lifecycle | conflicts with #23 (ROOM-90) resolved, see below | 113/113 |

Order rationale: #27 is stacked on #26; #25 is independent and small; #24 conflicted and
needed the others in develop first so the resolution could be tested against everything.

#24 resolution (documented on the PR): kept ROOM-88's versioned plan contract (the one
`DesignService`/persistence/assets use) and ported every ROOM-90 guardrail onto it, ported
ROOM-90's validator tests (5 kept + 2 added), renumbered its migration V7→V8 (V7 taken by
ROOM-101), and fixed one integration bug: recommendation sets could attach to PENDING/FAILED
designs once both PRs were combined. Merge commits only; no force-pushes.

No CI workflows exist in any repo (`.github/` holds only a PR template) — to be added.

## Backlog mapping (reconstructed, no Jira access)

| Ticket | Title (inferred) | Status |
|---|---|---|
| ROOM-87 | Structured budget-aware redesign plans | merged (#21) |
| ROOM-88 | Design input snapshot and output schema | merged (#24) |
| ROOM-89 | LLM provider adapter, secret-safe config | merged (#22) |
| ROOM-90 | Constraint-aware redesign prompt | merged (#23) |
| ROOM-91 | ? (redesign plan in UI) | not started |
| ROOM-92 | Persist design requests and results | merged (#24) |
| ROOM-93 | ? (visual prototype prompt from plan) | not started |
| ROOM-94 | Image-generation provider adapter | merged (#25), not wired to any endpoint |
| ROOM-95 | ? (generate + show visual prototype) | not started |
| ROOM-96 | Store generated prototypes | merged (#24) |
| ROOM-97 | ? (recommendation generation prompt/provider) | not started |
| ROOM-98 | Recommendation and DIY contracts | merged (#26) |
| ROOM-99/100 | Recommendation / DIY generation | not started (per #27 description) |
| ROOM-101 | Recommendation persistence and reads | merged (#27) |

## Gap analysis vs scope.md (MVP features)

| # | Feature | Backend | Frontend | Gap |
|---|---|---|---|---|
| F1 | Auth and project privacy | `users` table only; no endpoints, projects not owned | none | **missing** |
| F2 | Project setup + upload | create project takes no fields; upload + validation done | upload done | name / room type / purpose / rental status missing |
| F3 | Detection | done (CV + persistence) | done | — |
| F4 | Review + decisions | decision PATCH only | review done | manual add / edit / remove of objects missing |
| F5 | Style, budget, requirements | done | done | — |
| F6 | Redesign generator | plan generation done; image adapter not wired | none | prototype wiring + UI |
| F7 | Recommendations | contracts + persistence; no generation | none | generation + UI |
| F8 | Rental-friendly mode | enforced in plan prompt/validator | none | warnings + lease reminder in UI |
| F9 | Reuse + DIY | contracts + persistence; no generation | none | generation + UI |
| F10 | Budget summary | plan totals validated | none | summary UI, over-budget warning |
| F11 | Save + dashboard | get by id only | stub page | project list + dashboard |

Dead code noted: `OllamaClient`/`OllamaConfig` are not referenced by any service.

## Decisions from the project owner (2026-10-24)

- LLM/image provider: OpenAI; owner adds `OPENAI_API_KEY` to `roomify_backend/.env` locally.
- The AWS key in the local `.env` is a fresh key; the leaked key was already revoked.

## Phase 1 / Step 2 — features

| Date | Repo | PR | Feature | Result |
|---|---|---|---|---|
| 2026-10-24 | all | backend#29, cv#5, frontend#15 | First CI workflows (actions pinned by SHA) | merged, green |
| 2026-10-24 | backend | #28 | F1 auth: register/login/me, BCrypt, HS256 JWT, project ownership (foreign → 404) | merged, 119 tests, CI green |
| 2026-10-24 | frontend | #16 | F1 auth UI: login/register, httpOnly session cookie, proxy Bearer, protected pages | merged, 207 unit tests, CI green |

Fixes found on the way: `ai.api-key` ignored `OPENAI_API_KEY`; compose had a duplicate
`AI_MODEL` defaulting to empty; recommendation sets could attach to failed designs.

Pre-existing (on develop before any change): 7 mocked Playwright specs fail on chromium
(backendBridge ×3, roomFlow ×2, detectionReview ×2, the last flaky). Scheduled for step 3.
Local-dev note: Next normalizes `127.0.0.1` to `localhost` in redirects; use `http://localhost:3000`.
