# Demo video — script and shot list (target 4:30, hard limit 5:00)

Recorded parts come from `record-walkthrough.spec.ts` (Playwright, 1920×1080) and are cut together by
`assemble.sh` (ffmpeg). Segments marked **[PLACEHOLDER]** are slates for the team to replace with their
own footage and voice-over; nothing in them claims results that were not measured.

| # | Time | Shot | On screen | Voice-over (draft) |
|---|---|---|---|---|
| 1 | 0:00–0:20 | **[PLACEHOLDER] Team intro** | Slate "Team Bootcamp2Roomify" | Who we are and our roles. |
| 2 | 0:20–0:45 | Problem | Slate with the four problems from the scope | Students and renters can't picture a redesign in *their* room, can't see total cost, can't drill or paint, and throw away usable furniture. |
| 3 | 0:45–1:00 | Sign up, dashboard | `walkthrough` clip 1 | Every room is a private project under your account. |
| 4 | 1:00–1:25 | Create project + upload | clip 2 | Name it, say it's a rental, upload one photo. |
| 5 | 1:25–2:00 | Detection review | clip 3 | YOLO finds the main furniture. You decide Keep, Replace, Remove or Unsure, rename what it got wrong, and add what it missed — like the desk COCO can't detect. |
| 6 | 2:00–2:20 | Preferences | clip 4 | Style, colours, a 300-dollar budget; rental mode is already on because it's a rental. |
| 7 | 2:20–3:00 | Redesign plan | clip 5 | The plan keeps what you kept, replaces what you chose, stays inside the budget and avoids drilling — the server checks all of that before saving. |
| 8 | 3:00–3:25 | Visual concept | clip 6 | The same photo, redesigned. It's a concept, and we say so. |
| 9 | 3:25–4:00 | Suggestions, DIY, budget | clip 7 | Estimated prices, where each piece goes, a cheaper option, and a beginner DIY guide with safety notes. |
| 10 | 4:00–4:15 | Architecture | `architecture.png` | Next.js, Spring Boot, a FastAPI YOLO service, OpenAI, Postgres and S3. |
| 11 | 4:15–4:30 | Results | Slate with the numbers from EVALUATION.md | Measured on 60 held-out COCO rooms: detection precision 0.70, recall 0.62; plus the plan and cost numbers once measured. Be explicit about limitations. |
| 12 | 4:30–4:40 | **[PLACEHOLDER] Close** | Slate with repo links | Thanks. |

Rules for the voice-over: only quote numbers that appear in `EVALUATION.md`; call prices "estimates"
and the image a "concept".
