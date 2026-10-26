# Roomify demo video — script and shot list

Target 4:30 (hard limit 5:00). **Cut:** [`Roomify - Demo.mp4`](<Roomify - Demo.mp4>) (1920×1080, H.264, no audio),
built on 2026-10-26 from real-stack recordings. The AI scenes (8–10) were recorded in **demo mode**
(`AI_DEMO_MODE=true`): the OpenAI account was unusable at submission, so the plan, concept and
suggestions are pre-generated results and the app labels them "Demo" on screen.

How it is made:

1. Start the stack (Postgres, CV, backend, `next start`).
2. Record: `LIVE_RECORD=1 LIVE_SLOWMO=350 npx playwright test -c playwright.live.config.ts -g walkthrough`
   in roomify_frontend with `LIVE_AI=1` (backend with a funded key, or `AI_DEMO_MODE=true`) to record scenes 8–10.
3. Assemble: `video/assemble.sh <roomify_frontend>/test-results/walkthrough architecture/architecture.png "video/Roomify - Demo.mp4"`.
4. Replace the two placeholder slates (team intro, closing) and record the voice-over below in any editor.

Status: **recorded** = real footage · **recorded (demo mode)** = real app footage of demo-library
results · **placeholder** = for the team to film/record. The voice-over column is a draft for the team to
record; the video itself has no audio.

| # | Length (draft) | Shot | Status | On screen | Voice-over (draft — only quote numbers from EVALUATION.md) |
|---|---|---|---|---|---|
| 1 | 0:20 | Team intro | placeholder | Slate "Team intro" | Who we are and our roles. |
| 2 | 0:20 | Problem | recorded (slate) | Four problems from the scope | Students and renters can't picture a redesign in *their* room, can't see the total cost, can't drill or paint, and throw away usable furniture. |
| 3 | 0:14 | Sign up | recorded | Register → empty dashboard | Every room is a private project under your own account. |
| 4 | 0:13 | Create project + upload | recorded | Name "My dorm room", Dormitory, upload photo, preview | Name it, say where you live, upload one photo. |
| 5 | 0:20 | Detection review + manual add | recorded | Real YOLO boxes (bed 93 %, plant 85 %), Keep / Replace, add "desk" | The detector finds the main furniture. You decide Keep, Replace, Remove or Unsure — and add what it can't see, like this desk: its training data has no desk class. |
| 6 | 0:15 | Preferences | recorded | Scandinavian, 300 USD, colours, requirement; rental mode already on | Style, colours, a 300-dollar budget. Rental-Friendly Mode is on because it's a dorm. |
| 7 | 0:14 | Dashboard | recorded | Project card with photo and next step | Everything is saved; you can come back any time. |
| 8 | ~0:40 | Redesign plan | recorded (demo mode) | Plan with "Demo" note: keeping / replacing / adding, budget table, rental notes | In this demo build the plan comes from our pre-written demo library, because our AI credits ran out. It still follows your decisions and budget and avoids drilling — and the server checks all of that before it's saved, exactly as it checks the model's output. |
| 9 | ~0:25 | Visual concept | recorded (demo mode) | Before / example concept, "Demo" note | With a live model this is your own photo, redesigned. In demo mode it's an example photo for the room type, and the app says so. |
| 10 | ~0:35 | Suggestions, DIY, budget | recorded (demo mode) | Item cards, cheaper option, DIY guide, budget summary, "Demo" notes | Estimated prices, where each piece goes, a cheaper option, and a beginner DIY guide with safety notes. |
| 11 | 0:15 | Architecture | recorded (still) | `architecture.png` | Next.js, Spring Boot, a FastAPI YOLO service, OpenAI, Postgres and S3. |
| 12 | 0:15 | Results | recorded (slate) | Detection P 0.70 / R 0.62 / 32 ms; AI: demo mode | Measured on 60 held-out COCO rooms. The AI features ran in demo mode, so we report no AI accuracy, latency or cost numbers. |
| 13 | 0:10 | Close | placeholder | Slate with repo link | Thanks. |

Final length: see the cut above (under 5:00).

Rules for the voice-over: call prices "estimates" and the image a "concept"; say that AI results in
this build are demo-mode results; do not claim measured AI quality (EVALUATION.md §2 has no AI numbers).
