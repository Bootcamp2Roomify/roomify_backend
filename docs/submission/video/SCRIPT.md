# Roomify demo video — script and shot list

Target 4:30 (hard limit 5:00). **Draft cut:** [`Roomify - Demo (draft).mp4`](<Roomify - Demo (draft).mp4>)
(1920×1080, H.264, 2:55) built on 2026-10-24 from real-stack recordings; AI scenes are slates.

How it is made:

1. Start the stack (Postgres, CV, backend, `next start`).
2. Record: `LIVE_RECORD=1 LIVE_SLOWMO=350 npx playwright test -c playwright.live.config.ts -g walkthrough`
   in roomify_frontend (add `LIVE_AI=1` once an OpenAI key is configured to record scenes 7–9).
3. Assemble: `video/assemble.sh <roomify_frontend>/test-results/walkthrough architecture/architecture.png "video/Roomify - Demo.mp4"`.
4. Replace the placeholders and add the voice-over in any editor.

Status: **recorded** = real footage in the draft · **pending: requires OpenAI key** = slate in the
draft, recorded automatically with `LIVE_AI=1` · **placeholder** = for the team to film/record.

| # | Length (draft) | Shot | Status | On screen | Voice-over (draft — only quote numbers from EVALUATION.md) |
|---|---|---|---|---|---|
| 1 | 0:20 | Team intro | placeholder | Slate "Team intro" | Who we are and our roles. |
| 2 | 0:20 | Problem | recorded (slate) | Four problems from the scope | Students and renters can't picture a redesign in *their* room, can't see the total cost, can't drill or paint, and throw away usable furniture. |
| 3 | 0:14 | Sign up | recorded | Register → empty dashboard | Every room is a private project under your own account. |
| 4 | 0:13 | Create project + upload | recorded | Name "My dorm room", Dormitory, upload photo, preview | Name it, say where you live, upload one photo. |
| 5 | 0:20 | Detection review + manual add | recorded | Real YOLO boxes (bed 93 %, plant 85 %), Keep / Replace, add "desk" | The detector finds the main furniture. You decide Keep, Replace, Remove or Unsure — and add what it can't see, like this desk: its training data has no desk class. |
| 6 | 0:15 | Preferences | recorded | Scandinavian, 300 USD, colours, requirement; rental mode already on | Style, colours, a 300-dollar budget. Rental-Friendly Mode is on because it's a dorm. |
| 7 | 0:14 | Dashboard | recorded | Project card with photo and next step | Everything is saved; you can come back any time. |
| 8 | ~0:40 | Redesign plan | **pending: requires OpenAI key** | Plan: keeping / replacing / adding, budget table, rental notes | The plan keeps what you kept, changes what you chose, fits the budget and avoids drilling — and the server checks all of that before it's saved. |
| 9 | ~0:25 | Visual concept | **pending: requires OpenAI key** | Before / after | Your own photo, redesigned. It's a concept, and we say so. |
| 10 | ~0:35 | Suggestions, DIY, budget | **pending: requires OpenAI key** | Item cards, cheaper option, DIY guide, budget summary | Estimated prices, where each piece goes, a cheaper option, and a beginner DIY guide with safety notes. |
| 11 | 0:15 | Architecture | recorded (still) | `architecture.png` | Next.js, Spring Boot, a FastAPI YOLO service, OpenAI, Postgres and S3. |
| 12 | 0:15 | Results | recorded (slate) | Detection P 0.70 / R 0.62 / 32 ms; AI results pending | Measured on 60 held-out COCO rooms. *(Add plan compliance, latency and cost once measured; never estimate them.)* |
| 13 | 0:10 | Close | placeholder | Slate with repo link | Thanks. |

Expected final length with AI scenes: about 4:20.

Rules for the voice-over: call prices "estimates" and the image a "concept"; do not claim measured AI
quality until EVALUATION.md §2 has numbers.
