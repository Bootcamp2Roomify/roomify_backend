# Roomify — Project Scope

Source: https://docs.google.com/document/d/1ClGVq5p8Bbu6OvWE6dG4NiNY-qyntfOApeWIh34wON0
(the team's project scope document, exported 2026-10-24). Research citations are in the source doc.

## A. Problems

1. **Difficulty visualizing a personalized room redesign.** Inspiration platforms show other
   people's rooms; product photos don't show fit in the user's own space. Users without design
   experience struggle to combine colors, furniture and layout.
2. **Limited budgets and uncertain redesign costs.** Students and low-income users underestimate
   total cost (furniture, lighting, storage, decor, tools, tax, delivery) and don't know what to
   cut, swap, reuse or DIY when over budget.
3. **Rental restrictions.** Renters/dorm residents can't repaint, drill, replace flooring or mount
   furniture; most online ideas assume permanent changes.
4. **Unnecessary purchases and lack of DIY guidance.** Platforms push new products instead of
   reuse; DIY info is scattered and users can't judge cost, difficulty or safety.

## B. Solution

- Upload a photo of the actual room; detect major furniture; collect style and requirements;
  generate **one personalized redesign concept based on the user's own space**.
- Review detected furniture and classify each item **Keep, Replace, Remove or Unsure**.
- Collect max budget and priorities; provide **estimated prices, lower-cost alternatives, DIY
  options and an estimated total cost**.
- **Rental-Friendly Mode**: prioritize removable, non-permanent solutions.
- Encourage reuse of owned items — **"Redesign more, buy less."**
- Basic **DIY Assistant**: materials, tools, cost, difficulty, time, safety warnings, steps.
- Combine concept, decisions, recommendations, DIY and costs into **one saved room project**.
- Clearly label images, measurements, fit, prices and DIY instructions as **estimates**; no
  centimeter-perfect claims from one photo; not a substitute for professional advice.

## C. Primary target users

College students, renters, young adults, low-budget users, users without design experience.

## D. MVP features (acceptance criteria)

### F1 — User authentication and room project privacy
- New user can create an account → secure profile.
- Logged-in user sees only their own projects, images, concepts and recommendations.
- After logout, protected pages redirect to login.
- Uploaded images are accessible only through the owner's account and the correct project.
- Passwords are never stored in plain text.

### F2 — Room project setup and image upload
- "Create Project" collects project name, room type, room purpose and rental status.
- Each project gets a dedicated workspace.
- Accepts JPG, JPEG, PNG; unsupported type or oversize file → clear error.
- Uploaded image is linked to the correct project.

### F3 — AI room analysis and furniture detection
- Attempts to detect major visible furniture (beds, desks, chairs, sofas, TVs, plants, storage).
- Uncertain detections are not presented as guaranteed.
- Detected furniture is displayed for review.
- On analysis failure → error message, and the user can re-upload or continue manually.

### F4 — Furniture review and Keep / Replace / Remove / Unsure
- Review all detected items before generating.
- Edit or remove incorrect items; manually add missed items.
- Classify each item Keep, Replace, Remove or Unsure.
- Keep → attempt to include in redesign; Replace/Remove → considered by redesign.

### F5 — Style, budget and room requirements
- Select style (e.g., Minimalist, Cozy, Modern, Scandinavian) and colors.
- Enter maximum budget; priorities (storage, studying, relaxing, sleeping, space optimization).
- Enter special requirements; enable Rental-Friendly Mode.

### F6 — Personalized AI room redesign generator
- "Generate Redesign" produces one personalized concept from image + decisions + preferences.
- Keep items preserved where possible; Replace/Remove reflected where possible.
- Follows style/colors; recommendations consider budget; rental mode avoids permanent changes.
- Clearly states the visual is a concept, not a professional architectural plan.

### F7 — Furniture and decoration recommendations
- Item name and estimated price where possible; placement suggestion; short reason.
- Lower-cost alternative when an item may exceed budget.
- Prices clearly labeled as estimates, not real-time.

### F8 — Rental-Friendly Design Mode
- Prioritize non-permanent recommendations; avoid drilling, repainting, flooring, structural work.
- May suggest freestanding storage, removable lighting, Command hooks, tension rods, peel-and-stick.
- Warn when an item may damage property; remind users to verify with lease/dorm rules.

### F9 — Furniture reuse and basic DIY assistant
- Keep items reused; may suggest rearranging, repairing, repainting, decorating, repurposing.
- Beginner DIY projects with materials, tools, estimated cost, difficulty, time, safety warnings.
- No professional construction, electrical, structural or other high-risk DIY.

### F10 — Budget summary and cost planning
- Estimated cost per item; estimated total; compare with max budget.
- Clear warning when over budget; suggest cheaper alternatives, reuse or DIY.
- All costs labeled as estimates.

### F11 — Save room project and dashboard
- "Save Project" saves under the user's account (image, furniture, decisions, preferences, budget,
  concept, recommendations, DIY, cost).
- Saved projects listed on the dashboard; opening one shows its saved information.
- Multiple projects stay separate and linked to the correct room.

## Good-to-have (not required for MVP)

Multiple redesign variations, before/after comparison, AI design chat, interactive furniture
placement, fit checker, style quiz, space-optimization score, automatic shopping list, cheaper
alternative finder, multiple images per room, secondhand recommendations, native mobile app.

## F. Assumptions

Clear, well-lit photo of a significant part of the room; major furniture visible; users review
detections; accurate budget/preferences/rental info; **one room and one primary image per
project**; users understand outputs are concepts and estimates, verify dimensions, check lease
rules and follow safety precautions; stable internet; categories limited to common furniture.

## G. Technical limitations (must be disclosed)

1. Detection may misidentify or miss objects; users must be able to correct, remove or add.
2. No centimeter-perfect measurements from one photo.
3. Furniture fit cannot be guaranteed.
4. Generated prototype is a concept; may alter proportions, colors, doors, windows, lighting.
5. Kept furniture may not be reproduced exactly.
6. Prices are estimates unless backed by a validated real-time shopping API.
7. Recommendations may be general product types, not specific purchasable items.
8. DIY guidance is general; no electrical, plumbing, structural or high-risk instructions.
9. Rental rules vary; Rental-Friendly Mode cannot guarantee compliance.
10. Redesign is based only on what's visible in one photo plus user input.
