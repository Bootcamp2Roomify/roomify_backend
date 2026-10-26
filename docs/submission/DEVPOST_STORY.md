# Roomify — Devpost project page (draft text)

Paste into the Devpost form. Edit freely; numbers are only those measured in EVALUATION.md.

**Name:** Roomify
**Tagline:** Redesign the room you actually have — on a student budget, in a rental, keeping what you own.
**Try it out:** <https://roomify-rooms.vercel.app> (demo mode; the first request after a few idle minutes takes 10–20 s)

## Inspiration
Inspiration sites show other people's rooms. Students and renters can't picture changes in their own
space, can't see what a redesign really costs, often can't drill or paint, and end up replacing
furniture that still works. We wanted one place that starts from *your* photo and *your* limits.

## What it does
Upload one photo of your room. Roomify finds the main furniture; you decide Keep, Replace, Remove or
Unsure, fix anything it got wrong and add what it missed. Set a style, colours, a budget and
Rental-Friendly Mode. Roomify then produces a redesign plan that respects every decision and the
budget, a visual concept of your own room, shopping suggestions with estimated prices, placement and
cheaper alternatives, and beginner DIY guides with safety notes. Everything is saved to your account.

## How we built it
Next.js 15 frontend; Spring Boot 3.5 API with PostgreSQL 17; a FastAPI service running YOLO11n for
detection (on ONNX Runtime in production, so it fits a serverless function); all three run on Vercel,
the API as a container, with Postgres on Neon; OpenAI for plans, suggestions and DIY (JSON) and for editing the room photo. Generated output
is treated as untrusted: the server checks decisions, budget arithmetic, rental restrictions and DIY
safety before anything is saved, with one corrective retry.

## Challenges we ran into
Detection is imperfect (and COCO has no desk or wardrobe class), so review and manual entry became core
features. Making LLM output trustworthy needed strict schemas and server-side validation. Midway, OpenAI
retired the image model we had built on, so model choice became configuration. At submission our OpenAI
account had no usable credit, so we added a demo mode: the build you can try returns pre-generated plans,
an example concept photo for the room type, suggestions and DIY guides from a bundled library — fitted to
your furniture decisions and budget, passing the same server checks, and labelled "Demo" in the app. With a
funded key the same build calls OpenAI again, and falls back to the demo library if a call fails.

## Accomplishments that we're proud of
Privacy by design (another user's room is indistinguishable from a missing one), budget numbers computed
by the server rather than the model, and a measured, honest evaluation: detection precision 0.70 and
recall 0.62 on 60 held-out COCO room photos, 32 ms per photo on a laptop CPU.

## What we learned
Treat AI output as a draft that the product verifies, design for the AI being wrong or unavailable, and
measure instead of assuming.

## What's next for Roomify
An interior-specific detector (desks, wardrobes, shelves, lamps), multiple photos per room, live product
search with second-hand options, concept variations and a follow-up chat.

**Built with:** next.js, react, typescript, tailwindcss, spring-boot, java, postgresql, flyway, fastapi,
python, yolo, ultralytics, onnx-runtime, openai, vercel, neon, docker, playwright, github-actions
