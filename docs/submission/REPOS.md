# Repositories for judging

| Repo | Visibility now | Recommendation | README |
|---|---|---|---|
| [roomify_frontend](https://github.com/Bootcamp2Roomify/roomify_frontend) | Public | **Keep public** | setup, pages, env var, all test suites |
| [roomify_backend](https://github.com/Bootcamp2Roomify/roomify_backend) | Public | **Keep public** | setup, endpoint map, every env var, tests |
| [roomify_cv](https://github.com/Bootcamp2Roomify/roomify_cv) | Public | **Keep public** | model, API, private-network note, venv + container |

Why public: judges can read and run the code without access requests; the full history of all three
repos (all refs plus unreachable objects) was scanned for AWS keys, OpenAI/Google keys, private keys
and credential-like assignments on 2026-10-24 and **nothing was found**; only `.env.example` files with
placeholders have ever been committed, and `.env` is git-ignored everywhere.

No visibility change is needed. (None will be made without the project owner's approval.)

Licensing note for the owner: all three repos carry an MIT `LICENSE`, but the CV service depends on
**Ultralytics, which is AGPL-3.0**. Publishing the source satisfies AGPL's source-availability intent,
yet labelling roomify_cv "MIT" is likely inaccurate. Consider relicensing roomify_cv under AGPL-3.0
(not legal advice).
