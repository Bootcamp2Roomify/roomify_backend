# Evaluation Evidence

Detection numbers here were measured on 2026-10-24 (tests re-run 2026-10-26) by running the code in this repository; nothing is
estimated or copied from model cards. Where something has **not been measured yet**, it says so.
Reproduce with the scripts in [`evaluation/scripts/`](evaluation/scripts/).

## 1. Furniture detection (CV service)

**Set-up.** 60 images from **COCO val2017** (held out from YOLO11n's training data, human box
annotations): the 60 lowest image ids among the 334 val images that contain a bed or couch, i.e.
real indoor rooms. 159 ground-truth objects in Roomify's six classes. Each image was sent through
the product path — `POST /v1/analyze` on the running CV service (YOLO11n, `CONFIDENCE_THRESHOLD`
0.25) — and predictions were matched one-to-one per class at **IoU ≥ 0.5**; COCO crowd regions were
ignored. "Large" = object covering ≥ 2 % of the image (the furniture a redesign is about).

| Class | Ground truth | TP | FP | FN | Precision | Recall | Recall, large objects |
|---|---|---|---|---|---|---|---|
| bed | 34 | 25 | 4 | 9 | 0.86 | 0.74 | 0.73 (33) |
| sofa | 36 | 20 | 9 | 16 | 0.69 | 0.56 | 0.61 (33) |
| chair | 37 | 22 | 12 | 15 | 0.65 | 0.59 | 0.68 (19) |
| table | 11 | 6 | 9 | 5 | 0.40 | 0.55 | 0.67 (9) |
| tv | 15 | 11 | 1 | 4 | 0.92 | 0.73 | 0.80 (10) |
| plant | 26 | 15 | 7 | 11 | 0.68 | 0.58 | 0.67 (9) |
| **all** | **159** | **99** | **42** | **60** | **0.70** | **0.62** | **0.68 (113)** |

Raw per-image results: [`evaluation/data/detection_results.json`](evaluation/data/detection_results.json);
image ids: [`evaluation/data/coco_subset_ids.json`](evaluation/data/coco_subset_ids.json).

**Latency** (same 60 images, Apple-silicon laptop CPU, no GPU): model time p50 **32 ms**, p95 **41 ms**;
HTTP round trip p50 35 ms, p95 44 ms, max 278 ms (first request, model warm-up).

**What this means for the product.** Roughly one object in three is missed and three in ten
detections are wrong, so detection is a *starting list*, not an inventory — which is why the review
step lets people rename, remove and add items, and why every detection under 60 % confidence is
labelled "please check". Beds and TVs are reliable; tables are not (0.40 precision).

### Deployed runtime: ONNX Runtime on Vercel (measured 2026-10-26)

The live CV service runs the same YOLO11n weights exported to ONNX on ONNX Runtime (no torch), so it
fits a Vercel Python Function. Same 60 images, same script and matching rules:

| Runtime | TP | FP | FN | Precision | Recall | Recall, large | Model time p50 / p95 |
|---|---|---|---|---|---|---|---|
| torch (table above), laptop | 99 | 42 | 60 | 0.70 | 0.62 | 0.68 | 32 / 41 ms |
| ONNX Runtime, laptop | 101 | 42 | 58 | 0.71 | 0.64 | 0.70 | 38 / 43 ms |
| ONNX Runtime, live on Vercel | 101 | 42 | 58 | 0.71 | 0.64 | 0.70 | 237 / 257 ms |

The ONNX path letterboxes to a fixed 640×640 square instead of ultralytics' minimal rectangle, which
explains the two extra true positives; this is the same detector, not an improvement. On Vercel
(one vCPU) a photo takes about 0.24 s of model time; the HTTP round trip measured from the test laptop
in Asia to the US East function was p50 1.4 s, p95 2.0 s, max 3.5 s (cold start included), which
says more about the network path than about the service. Memory: about 175 MB for the ONNX service
vs about 510 MB with torch. Raw results:
[`detection_results_onnx_local.json`](evaluation/data/detection_results_onnx_local.json),
[`detection_results_vercel.json`](evaluation/data/detection_results_vercel.json).

### Failure cases

In each figure, green boxes are ground truth and red boxes are predictions with their confidence.
Images are copyright their Flickr authors via COCO (annotations CC BY 4.0) and are shown for
evaluation only.

![Failure case COCO 47769: a wooden window shutter is detected as a chair (confidence 0.50); two partly hidden chairs and a small table are missed; the floor lamp cannot be detected at all because COCO has no lamp class.](evaluation/failures/coco-47769.jpg)

![Failure case COCO 39477: the sofa and TV are found, but two hanging planters merge into one box (one false positive and one miss) and three small plants under 3 % of the frame are missed.](evaluation/failures/coco-39477.jpg)

![Failure case COCO 13923: in a bright living room the sofa (0.90) and large chairs are found, but seven smaller objects (each at most 5.5 % of the image) are missed; there are no false alarms.](evaluation/failures/coco-13923.jpg)

![Failure case COCO 14038: in a cluttered studio the bed is found; the TV, a small table and other items under 5 % of the frame are missed. The shelf plant is detected but overlaps its annotation by less than IoU 0.5, so one localisation error counts as both a false alarm and a miss.](evaluation/failures/coco-14038.jpg)

### Limitations of this measurement

- 60 images is small: one more miss moves a class's recall by ~3 points. Confidence intervals are wide.
- COCO photos are not exactly what users upload (often wider, more cluttered, lower light).
- COCO has **no desk, wardrobe, shelf, lamp or rug classes**, so the most common student-room
  furniture is never detected; the scope's "storage furniture" relies entirely on manual entry.
- Measured at the service's single confidence threshold (0.25); no threshold tuning was done.

## 2. Redesign plan, visual concept and suggestions (OpenAI)

**Demo mode (pre-generated results).** No OpenAI measurements exist. On 2026-10-26 the project's
OpenAI account could not serve requests: one key was rejected (`401 invalid_api_key`) and the other
had no credit (`429 insufficient_quota`, `credit_balance_exhausted`). The submitted build therefore
runs with `AI_DEMO_MODE=true`: the plan, visual concept, suggestions and DIY guides come from a
bundled demo library and every such result is marked `source: "demo"` in the API and "Demo" in the
UI (see §2.1). None of the values below are estimated or borrowed from model cards; latency and cost
of demo results say nothing about the model, so they are not reported.

| Result | Value |
|---|---|
| Plan budget compliance (first attempt / after corrective retry) | **Demo mode (pre-generated results)** |
| Plan decision compliance (KEEP kept, REMOVE removed) | **Demo mode (pre-generated results)** |
| Rental-mode compliance (no permanent work) | **Demo mode (pre-generated results)** |
| Suggestion sets within budget; cheaper-alternative coverage | **Demo mode (pre-generated results)** |
| DIY guides rejected by the safety filter | **Demo mode (pre-generated results)** |
| Visual-concept quality (architecture preserved, kept items visible) | **Demo mode (pre-generated results)** |
| Latency p50 / p95: plan, concept, suggestions, DIY | **Demo mode (pre-generated results)** |
| Cost per full redesign (tokens × list price on the run date) | **Demo mode (pre-generated results)** |

### 2.1 What demo mode does (verified by tests)

- Four cases chosen by room purpose: living room (`LIVING`), bedroom (`SLEEP`), home office
  (`STUDY`), student room (`STUDY_AND_SLEEP`, `MULTIPURPOSE`, default).
- The demo plan is built from the request — every detected object with its KEEP / REPLACE / REMOVE /
  UNSURE decision, the budget, currency and rental flag — and must pass the same `DesignPlanValidator`
  as model output. Suggestions and DIY guides pass the same contract, safety and rental checks.
- The concept image is a CC0 stock photo for the room type (credits: `src/main/resources/demo/CREDITS.md`),
  **not** an edit of the user's photo; the UI says so.
- With a funded key (`AI_DEMO_MODE=false`) the app calls OpenAI; `AI_DEMO_FALLBACK=true` (default)
  answers from the library when a call fails (401, 429, timeout, invalid output) and logs
  `AI demo result used: feature=… case=… reason=…`.
- Tests: `DemoLibraryTest` (every case through the validators, odd and zero budgets, TWD, rental,
  low-confidence items) and `DemoFallbackTest` (each endpoint in demo mode without calling the provider;
  quota, rejected-key, timeout, unexpected-error and invalid-output fallback).

The live-AI measurement harness is ready for when a funded key is available:

- the backend logs one content-free line per provider call
  (`AI usage kind=text|image model=… promptTokens=… completionTokens=… reasoningTokens=… latencyMs=…`);
- `tests/live` in roomify_frontend runs the full journey with `LIVE_AI=1`.

Planned measurements (same rooms, fixed budgets of 150 / 300 / 600 USD, rental on and off):

| Metric | How it will be measured |
|---|---|
| Plan budget compliance | share of first-attempt plans whose allocation sum equals the total and is ≤ budget (before the server's validator); share stored after the one corrective retry |
| Decision compliance | share of plans where every KEEP item is kept and every REMOVE item removed (validator outcome) |
| Rental compliance | share of rental-mode plans rejected for permanent-modification wording |
| Suggestion budget fit | share of first-attempt suggestion sets within budget; cheaper-alternative coverage |
| DIY safety | share of guides rejected by the safety filter |
| Latency | p50 / p95 per call type from the usage log |
| Cost | tokens × OpenAI list prices on the day of the run |

## 3. Behaviour without an OpenAI key (verified)

`MissingAiKeyTest` runs the real OpenAI provider beans with no key; a blank key, an unedited
`.env.example` placeholder and a key rejected by OpenAI (401/403) all count as "not configured".

| Action | Response | State afterwards |
|---|---|---|
| Generate plan (twice) | `503 AI_CONFIGURATION_ERROR` | attempt stored as FAILED with that code, nothing pending, project status unchanged, retry allowed |
| Visual concept | `503 AI_CONFIGURATION_ERROR` | no image asset written |
| Suggestions | `503 AI_CONFIGURATION_ERROR` | no suggestion set written |
| DIY guide | `503 AI_CONFIGURATION_ERROR` | no guide written; saved plan still readable |

The UI shows: "AI generation is not configured on this server yet (no valid OpenAI key). Your photo,
furniture and preferences are saved — try again once AI is enabled." (frontend tests cover all four
actions; the real-stack browser journey shows it for the plan).

## 4. Automated test results (2026-10-26, develop, release v1.0.1)

| Suite | Result |
|---|---|
| Backend (JUnit, Postgres 17) | 186 passed (25 for demo mode) |
| CV service (pytest) | 22 passed on torch, 22 passed on ONNX Runtime |
| Frontend unit (Vitest) | 235 passed |
| Browser, stubbed API (Playwright, chromium + mobile) | 39 passed |
| Browser, fake Spring API through Next (Playwright) | 6 passed |
| Browser, real stack, `AI_DEMO_MODE=true` (Playwright `tests/live`, `LIVE_AI=1 LIVE_DEMO=1`) | full journey incl. plan, concept, suggestions, DIY and over-budget warning; privacy; logout; CV-down manual entry: all passed |
| Browser, **live deployment** <https://roomify-rooms.vercel.app>, demo mode (same suite, `LIVE_BASE_URL`) | journey incl. plan, visual concept (image loaded), suggestions and DIY; privacy; logout: passed. CV-down skipped (needs the CV service stopped). The journey also passed with a 9.7 MB, 6000×4000 photo (shrunk in the browser below Vercel's 4.5 MB body limit). One earlier run timed out at registration (15 s limit) while a new API instance cold-started; it passed on rerun |
