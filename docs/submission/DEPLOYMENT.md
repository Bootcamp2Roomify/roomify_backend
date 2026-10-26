# Deployment

**Status (2026-10-26): approved, configs committed, Neon database created, not yet deployed.** Blocked on
(1) a payment method on the Fly.io account (`fly apps create` answers "We need your payment information")
and (2) a bucket-scoped AWS key. The judging build runs with `AI_DEMO_MODE=true` (see step 3).

## Recommendation: Vercel + Fly.io + Neon + existing S3

![Proposed Roomify deployment: Vercel for the frontend, Fly.io for the API and a private CV service, Neon Postgres, the existing S3 bucket and the OpenAI API.](architecture/deployment.png)

Why this shape:

- **Each service already ships as a container** (backend and CV Dockerfiles are pinned and tested),
  so the hosts only run images: no build-system work.
- **The CV service gets no public address.** It has no authentication and an `image_url` input;
  Fly's private network lets only the backend reach it.
- **Long AI calls fit.** Image edits can take up to ~2 minutes. Vercel Hobby functions with Fluid
  compute allow 300 s; the backend's AI timeout is 180 s. The browser never calls the backend directly
  (the Next bridge does), so no CORS changes are needed.
- **Measured memory fits 1 GB machines:** CV container 374 MiB after inference, backend 424 MiB
  after start-up (Docker, 2026-10-24).

## Cost (list prices checked 2026-10-24)

| Piece | Plan | Monthly |
|---|---|---|
| Frontend | Vercel Hobby (personal/non-commercial — fine for judging) | $0 |
| Backend | Fly.io shared-cpu-1x, 1 GB, always on, `iad` | $6.70 |
| CV service | Fly.io shared-cpu-1x, 1 GB, always on, `iad` | $6.70 |
| Database | Neon Free (0.5 GB storage, scales to zero after 5 min idle) | $0 |
| Images | Existing AWS S3 bucket (Standard ≈ $0.023/GB-month) | < $0.10 |
| **Infrastructure total** | | **≈ $13.40 / month** |
| OpenAI usage | per redesign: plan + concept + suggestions + DIY | **Demo mode (pre-generated results)** — $0 while `AI_DEMO_MODE=true` (EVALUATION.md §2) |

Notes: Fly bills per second, so stopping the machines after judging stops the cost. Fly raised its
RAM price on 2026-10-01 (1 GB machine $5.70 → $6.70); Frankfurt is $7.73. Neon's first query after
idling has a cold start of up to a few seconds. Sources: [Fly.io pricing](https://docs.fly.io/about/pricing),
[Vercel function limits](https://vercel.com/docs/functions/limitations), [Neon pricing](https://neon.com/pricing).

## Steps (to run after approval)

Prerequisites the owner provides: Fly.io account (`fly auth login`), Vercel account linked to the
GitHub org, Neon account, the existing S3 bucket name/region and an IAM key **scoped to that bucket**,
an OpenAI key.

1. **Database** — `neonctl projects create --name roomify --region-id aws-us-east-1`, then
   `neonctl connection-string --project-id <id>` for host, database, user and password.
2. **CV service** (private) — `roomify_cv/fly.toml` is committed: no public service, `CV_HOST="::"`
   because Fly's private network (`roomify-cv.internal`) is IPv6-only and the image default `0.0.0.0` is IPv4.
   ```bash
   cd roomify_cv
   fly apps create roomify-cv
   fly deploy -a roomify-cv
   fly ips list -a roomify-cv   # must be empty: no public address
   ```
3. **Backend** — `roomify_backend/fly.toml` is committed (1 GB, always on, `/api/health` check,
   `VISION_SERVICE_BASE_URL=http://roomify-cv.internal:8000`, `STORAGE_TYPE=s3`).
   ```bash
   cd roomify_backend
   fly apps create roomify-api
   fly secrets set -a roomify-api \
     SPRING_DATASOURCE_URL='jdbc:postgresql://<neon-host>/<db>?sslmode=require' DB_USERNAME=<user> DB_PASSWORD=<password> \
     JWT_SECRET=$(openssl rand -hex 32) OPENAI_API_KEY=<key> \
     S3_BUCKET=<bucket> AWS_REGION=<region> AWS_ACCESS_KEY_ID=<id> AWS_SECRET_ACCESS_KEY=<secret>
   fly deploy -a roomify-api
   curl https://roomify-api.fly.dev/api/health
   ```
   **Demo mode:** `fly.toml` sets `AI_DEMO_MODE=true`, so the plan, visual concept, suggestions and DIY guides come
   from the bundled demo library (fitted to the user's furniture decisions and budget; the concept is a stock photo
   for the room type) and every result is marked `source: "demo"`. With `AI_DEMO_MODE=false` the app calls OpenAI and,
   because `AI_DEMO_FALLBACK` defaults to `true`, still answers from the library if a call fails (401, 429, timeout,
   invalid output); each fallback is logged as `AI demo result used: feature=… case=… reason=…`.
   Neon requires TLS, so the JDBC URL is set whole with `sslmode=require` (it overrides `DB_HOST`/`DB_PORT`/`DB_NAME`).
   **Long AI calls:** Fly's proxy closes a connection after 60 s with no bytes; an image edit can be silent for
   ~2 minutes, so `fly.toml` sets `http_options.idle_timeout = 600`. A Fly community report says this setting may
   not take effect: the smoke test (step 5) must include a visual concept. If it is cut off, the fix is to make the
   concept endpoint asynchronous (202 + poll).
4. **Frontend** — Vercel → *Add New Project* → import `Bootcamp2Roomify/roomify_frontend` → framework
   Next.js → environment variable `ROOMIFY_API_URL=https://roomify-api.fly.dev` → *Deploy*. The project API bridge
   sets `maxDuration = 300` so AI calls are not cut at Vercel's non-Fluid 10 s default.
5. **Smoke test** — run the live browser suite against the deployment:
   `LIVE_BASE_URL=https://<project>.vercel.app LIVE_AI=1 npx playwright test -c playwright.live.config.ts`.
6. **After judging** — `fly scale count 0 -a roomify-api -a roomify-cv` (stops billing), or `fly apps destroy`.

## Risks

- `gpt-4o-mini` (default text model) is shut down by OpenAI on **2027-01-14**; switch to
  `gpt-6-luna` with `AI_TEMPERATURE=` and `AI_REASONING_EFFORT=low` before then (config only).
- Vercel Hobby is for non-commercial use; move to Pro ($20/month) for anything beyond judging.
- A public demo can spend the OpenAI budget: set a monthly usage limit in the OpenAI dashboard.
