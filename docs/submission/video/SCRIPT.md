# Roomify demo video — script and shot list

**Final cut:** [`Roomify - Demo.mp4`](<Roomify - Demo.mp4>) — 2:43, 1920×1080, H.264, **no audio and no
voice-over**. Every scene carries an on-screen caption; a title card opens the video and an end card
shows the live URL, <https://roomify-rooms.vercel.app>. Built on 2026-10-26 from recordings of the live
deployment.
The AI scenes (6–8) were recorded in **demo mode** (`AI_DEMO_MODE=true`): the OpenAI account was
unusable at submission, so the plan, concept and suggestions are pre-generated results, and both the
app and the captions say so.

## How it is made

1. Warm the deployment (`curl https://roomify-rooms.vercel.app/api/health`), or start a local stack with
   `AI_DEMO_MODE=true`.
2. Record, in roomify_frontend (`LIVE_RECORD_DIR` outside `test-results`, which every Playwright run clears):
   `LIVE_BASE_URL=https://roomify-rooms.vercel.app LIVE_RECORD=1 LIVE_RECORD_DIR=/tmp/walkthrough LIVE_AI=1 LIVE_DEMO=1 LIVE_SLOWMO=350 npx playwright test -c playwright.live.config.ts -g walkthrough`.
   Scenes 1–5 are screen recordings. The long, scrolling scenes (plan, concept, suggestions + DIY) are
   saved as full-resolution screenshots (`frame-NN.png`, shown 4 s each): Playwright's screen recorder
   tears frames during scrolling, and the earlier cut had frames caught mid-scroll. Each still waits
   until every image in view has loaded.
3. Assemble:
   `video/assemble.sh /tmp/walkthrough architecture/architecture.png "video/Roomify - Demo.mp4" roomify-rooms.vercel.app`.
   Captions and cards are HTML rendered by headless Chrome and laid over the clips with ffmpeg.

## Shot list

| # | Length | Shot | Source | Caption on screen |
|---|---|---|---|---|
| 0 | 0:06 | Title card | card | "Roomify — Redesign the room you actually have." For students and renters: see a redesign of your own room, within your budget, without drilling or throwing furniture away. |
| 1 | ~0:13 | Sign up | recording | Sign up — every room is a private project under your account |
| 2 | ~0:13 | Create project + upload | recording | Name the room, say where you live, upload one photo |
| 3 | ~0:20 | Detection review + manual add | recording | YOLO finds the furniture. You choose Keep, Replace, Remove or Unsure — and add what it missed |
| 4 | ~0:15 | Preferences | recording | Style, colours and a budget. Rental-Friendly Mode is on because it's a dorm |
| 5 | ~0:10 | Dashboard | recording | Everything is saved. Come back any time |
| 6 | 0:20 | Redesign plan (demo mode) | 5 stills | The redesign plan follows your decisions and budget — and the server checks it before saving. Demo mode: pre-generated results, labelled Demo |
| 7 | 0:08 | Visual concept (demo mode) | 2 stills | Visual concept. In demo mode it's an example photo for this room type, not an edit of your photo |
| 8 | 0:16 | Suggestions + DIY (demo mode) | 4 stills | Suggestions within budget, cheaper options and a beginner DIY guide with safety notes |
| 9 | 0:09 | Architecture | still | Three Vercel projects: Next.js · Spring Boot container · FastAPI + YOLO11n (ONNX) · Postgres on Neon |
| 10 | 0:07 | Results | card | Detection on 60 held-out COCO room photos: precision 0.70 · recall 0.62 · 32 ms per photo on a laptop CPU. AI features run in demo mode, so no AI accuracy, latency or cost is claimed |
| 11 | 0:07 | End card | card | "Try Roomify" — roomify-rooms.vercel.app · github.com/Bootcamp2Roomify |

Rules for the captions: call prices "estimates" and the image a "concept"; say that AI results in this
build are demo-mode results; quote only numbers measured in EVALUATION.md.
