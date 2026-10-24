# Evaluation

All numbers here were measured on 2026-10-24 by running the code in this repository; nothing is
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

### Failure cases

Green = ground truth, red = prediction with confidence. Images © their Flickr authors via COCO
(CC BY 4.0 annotations); shown for evaluation only.

| Image | What went wrong |
|---|---|
| ![](evaluation/failures/coco-47769.jpg) | A wooden **window shutter is detected as a chair (0.50)**; two partly hidden chairs and a small table are missed; the floor lamp cannot be detected at all (COCO has no lamp class). |
| ![](evaluation/failures/coco-39477.jpg) | Sofa and TV found correctly, but **two hanging planters merge into one box** (one FP + one FN) and three small plants (< 3 % of the frame) are missed. |
| ![](evaluation/failures/coco-13923.jpg) | Bright, well-lit living room: the sofa (0.90) and the large chairs are found, but seven smaller objects (each ≤ 5.5 % of the image — chairs behind the table, the window-sill plants, the tv) are missed. No false alarms: small items are where recall drops. |
| ![](evaluation/failures/coco-14038.jpg) | Cluttered studio: the bed is found; the tv, a small table and other items under 5 % of the frame are missed. The plant on the shelf **is** detected, but its box overlaps the annotation by less than IoU 0.5, so one localization error counts as both a false alarm and a miss. |

### Limitations of this measurement

- 60 images is small: one more miss moves a class's recall by ~3 points. Confidence intervals are wide.
- COCO photos are not exactly what users upload (often wider, more cluttered, lower light).
- COCO has **no desk, wardrobe, shelf, lamp or rug classes**, so the most common student-room
  furniture is never detected; the scope's "storage furniture" relies entirely on manual entry.
- Measured at the service's single confidence threshold (0.25); no threshold tuning was done.

## 2. Redesign plan, visual concept and suggestions (OpenAI)

**Not yet measured.** These require `OPENAI_API_KEY`, which was not configured on the evaluation
machine at the time of writing. The harness is ready:

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

## 3. Automated test results (2026-10-24, develop)

| Suite | Result |
|---|---|
| Backend (JUnit, Postgres 17.11) | 142 passed |
| CV service (pytest) | 21 passed |
| Frontend unit (Vitest) | 228 passed |
| Browser, stubbed API (Playwright, chromium + mobile) | 39 passed, stable over 4 consecutive runs |
| Browser, fake Spring API through Next (Playwright) | 6 passed |
| Browser, real stack — no AI key (Playwright `tests/live`) | journey, privacy, logout, CV-down manual entry: all passed |
