# Implementation Plan

Feature breakdown for the Mini AI Support Inbox, in the order the features should be built.
The requirements come from `docs/ASSIGNMENT.md`. Architecture, conventions, and
constraints are defined in `CLAUDE.md`; this file defines **what** to build and
**in which order**. If any of these files conflict, stop and ask.

## Instructions for Claude Code

**The developer must approve every feature before you proceed to the next one.**

For each feature, follow this cycle exactly:

1. **Plan.** Before writing code, post a short plan: the files you will create or change,
   key design decisions, and anything in this file or `CLAUDE.md` that seems unclear.
   Wait for the developer to confirm the plan.
2. **Build.** Implement only the current feature. Do not start, scaffold, or "prepare"
   anything belonging to a later feature.
3. **Verify.** Run the tests and checks listed for the feature. All must pass.
4. **Report.** Summarize: what was built, files changed, test results, any deviations
   from the plan or spec (and why), and open questions.
5. **Stop and wait.** Do not continue until the developer explicitly approves the feature
   (e.g. "approved", "proceed to F4"). Silence, a question, or a partial comment is
   **not** approval. If the developer requests changes, make them and repeat steps 3–5.
6. **After approval:** tick the feature's box in the progress checklist below.
   Do not commit; the developer reviews and commits each feature using the suggested
   commit message (or asks you to commit it).

Additional rules:
- Never mark a feature as done without explicit approval.
- Never skip a feature or change the order without the developer's agreement.
- Changes to earlier features that are explicitly listed in the current feature's scope
  or verify step (e.g. F5 updating the F4 tests) are part of the plan; include them in
  your plan for the feature. Any other change to an approved feature: stop and explain
  it before making it.
- Optional features are built only if the developer explicitly asks for them.

## Progress

- [x] F1 Project skeleton and local environment
- [x] F2 Database schema and persistence
- [x] F3 Domain logic and status lifecycle
- [x] F4 REST API and error handling
- [ ] F5 Authentication and CORS
- [ ] F6 OpenRouter AI integration
- [ ] F7 Rate limiting
- [ ] F8 Frontend foundation and login
- [ ] F9 Message history view
- [ ] F10 New message form
- [ ] F11 Message detail: edit, approve, regenerate
- [ ] F12 (Optional) Per-customer history
- [ ] F13 Deployment and documentation

---

## F1 Project skeleton and local environment

**Goal:** a monorepo that builds and runs locally, with nothing feature-specific yet.

Scope:
- `/backend`: Spring Boot 4.1.x app on Java 25, generated from Spring Initializr
  (Maven Wrapper), dependencies from `CLAUDE.md`, Actuator with `/actuator/health`,
  virtual threads enabled.
- `/frontend`: Vite `react-ts` app (React 19, React Router 7) with lint and test scripts;
  Node 24 pinned in `.nvmrc` and `package.json` `engines`.
- `docker-compose.yml` with a `postgres:17` service for local development.
- Root `.gitignore`, `.env.example` files with placeholders only.
- Minimal `README.md` with local setup steps.

Acceptance criteria:
- `docker compose up -d postgres`, then `./mvnw spring-boot:run` starts the backend and
  `/actuator/health` returns UP.
- `npm run dev` serves the frontend; `npm run build` and `npm run lint` pass.
- No secrets anywhere in the repo.
- Versions match the table in `CLAUDE.md`; exact resolved versions are recorded in `README.md`.

Verify: `./mvnw verify`, `npm run build`, `npm run lint`.

Commit: `chore: set up monorepo skeleton with backend, frontend and local Postgres`

---

## F2 Database schema and persistence

**Goal:** the `messages` table and its JPA mapping.

Scope:
- Flyway migration `V1__create_messages.sql` exactly as specified in `CLAUDE.md`
  (UUID, CHECK constraints, indexes, `version` column).
- `Message` entity, `MessageStatus` and `Category` enums, `MessageRepository`.
- `ddl-auto=validate`.

Acceptance criteria:
- Application starts against an empty database and Flyway applies V1.
- Hibernate schema validation passes.
- DB rejects invalid status/category values and empty or overlong content.

Verify: Testcontainers repository test (save, load, constraint violation).

Commit: `feat: add messages schema, entity and repository`

---

## F3 Domain logic and status lifecycle

**Goal:** the core business logic, independent of HTTP and of any real LLM.

Scope:
- `AiAssistant` interface (`classify`, `draftReply`) plus a stub implementation used
  until F6.
- `MessageService`: create (save PENDING_AI → classify → draft → DRAFT_READY or AI_FAILED),
  edit reply, approve, regenerate.
- Status transition rules in one place; invalid transitions throw a domain exception.
- Short transactions in a separate bean; no transaction open during AI calls.

Acceptance criteria:
- All transitions in the `CLAUDE.md` lifecycle work; all others are rejected.
- `ai_draft` is never overwritten by an edit.
- AI failure leaves the message saved as AI_FAILED with an error code.

Verify: unit tests with a mocked `AiAssistant`: happy path, failure during
classification → AI_FAILED without calling `draftReply`, failure during drafting →
AI_FAILED, every invalid transition, regenerate from AI_FAILED and DRAFT_READY.
(Parsing invalid model output into OTHER belongs to the AI client and is tested in F6.)

Commit: `feat: implement message service and status lifecycle`

---

## F4 REST API and error handling

**Goal:** the HTTP API from `CLAUDE.md`, still using the stub AI.

Scope:
- `MessageController` with all `/api/messages` endpoints, record DTOs, explicit mapping.
- Bean Validation with field limits; pagination (default 20, max 100), filters by
  status and category, sorted newest first.
- `GlobalExceptionHandler` returning `ProblemDetail` for 400, 404, 409 (invalid
  transition and optimistic lock conflict), and 500 without internal details.

Acceptance criteria:
- `POST /api/messages` returns 201, including when AI fails (body shows AI_FAILED).
- Validation errors list the offending fields.
- A stale `version` on edit/approve returns 409.

Verify: MockMvc + Testcontainers integration tests: create → edit → approve flow,
validation errors, 404, 409 on invalid transition and on version conflict, list filters.

Commit: `feat: add messages REST API with ProblemDetail error handling`

---

## F5 Authentication and CORS

**Goal:** every API endpoint except health requires the admin user.

Scope:
- Spring Security: stateless HTTP Basic, in-memory user from `ADMIN_USERNAME` and
  `ADMIN_PASSWORD_HASH` (bcrypt).
- Custom entry point: 401 `ProblemDetail` without `WWW-Authenticate`.
- CSRF disabled with an explanatory comment; OPTIONS preflight permitted.
- CORS restricted to `CORS_ALLOWED_ORIGINS`.
- `GET /api/auth/me`.

Acceptance criteria:
- `/actuator/health` is public; all `/api/**` endpoints return 401 without valid credentials.
- 401 responses have no `WWW-Authenticate` header.
- Disallowed origins are rejected by CORS.

Verify: integration tests for 401 (missing and wrong credentials), 200 with valid
credentials, health without credentials, `/api/auth/me`, CORS preflight. Update F4 tests
to authenticate.

Commit: `feat: add stateless basic auth and CORS configuration`

---

## F6 OpenRouter AI integration

**Goal:** replace the stub with real classification and reply drafting.

Scope:
- `OpenRouterAiAssistant` using `RestClient`, configured via `AiProperties`.
- Prompts in `Prompts.java`; customer text wrapped in delimiters; reply in the
  customer's language; category passed to the draft prompt.
- Classification via `response_format` `json_schema`, returning `{"category": "..."}`,
  parsed with fallback to OTHER exactly as specified in `CLAUDE.md`.
- Draft output as plain text per the draft output rules in `CLAUDE.md`.
- Timeouts, `max_tokens`, retry once on 429/5xx only, mapping to `AiErrorCode`.
- The stub moves to test configuration: all existing and new integration tests keep
  using it (or a mock) and must never call OpenRouter.
- The developer puts the real API key in a local, gitignored `.env`. Never read,
  print, or log its value.

Acceptance criteria:
- Real end-to-end run locally classifies and drafts a Slovenian and an English message.
- No API key or provider error body appears in logs or responses.

Verify: `MockRestServiceServer` tests for response parsing (including fenced JSON,
lowercase values, and extra fields), invalid JSON and unknown values → OTHER,
each error code mapping, retry on 429/503, no retry on timeout/401/402. One manual
end-to-end check by the developer with a real key.

Commit: `feat: integrate OpenRouter for classification and reply drafting`

---

## F7 Rate limiting

**Goal:** cap LLM cost on the public deployment.

Scope:
- Bucket4j filter with one global bucket for `POST /api/messages` and
  `POST /api/messages/{id}/regenerate`, limit from `RATE_LIMIT_AI_PER_MINUTE`.
- 429 `ProblemDetail` with a `Retry-After` header.

Acceptance criteria:
- Requests beyond the limit return 429; other endpoints are unaffected.

Verify: integration test with a low configured limit.

Commit: `feat: rate limit AI endpoints`

---

## F8 Frontend foundation and login

**Goal:** the frontend can authenticate and talk to the backend.

Scope:
- `src/api/` fetch wrapper: base URL from `VITE_API_BASE_URL`, `Authorization` header,
  60s `AbortController` timeout, typed `ProblemDetail` errors.
- Auth context holding credentials in memory only.
- Login view calling `/api/auth/me`; any 401 returns to login.
- Routing skeleton for list, new, and detail views.
- Basic "server is waking up" handling for slow first responses.

Acceptance criteria:
- Valid credentials log in; invalid ones show an error; refresh logs out (intended).

Verify: tests for the API wrapper (headers, timeout, error parsing); manual login check.

Commit: `feat: add frontend API client, auth context and login`

---

## F9 Message history view

**Goal:** the "pregled zgodovine sporočil" requirement.

Scope:
- List view: newest first, status and category badges, filters, pagination,
  empty and error states. Rows link to the detail view.

Acceptance criteria:
- Filters and pagination match backend behavior; loading and error states are visible.

Verify: component test for rendering and filters; manual check.

Commit: `feat: add message history view with filters and pagination`

---

## F10 New message form

**Goal:** enter a customer message and get a classified draft.

Scope:
- Form with content (required, max 5000, live character count), optional name and email.
- Clear loading state while AI runs; field errors from 400 responses.
- On success, navigate to the new message's detail view (also when AI_FAILED).

Acceptance criteria:
- Client-side and server-side validation errors are both shown clearly.

Verify: component test for validation; manual end-to-end check.

Commit: `feat: add new message form`

---

## F11 Message detail: edit, approve, regenerate

**Goal:** the "urejanje in potrditev odgovora" requirement.

Scope:
- Detail view: original message, customer info, category, timestamps, read-only AI draft,
  editable final reply (prefilled from the draft).
- Save, Approve, and Regenerate actions, enabled only in valid statuses.
- AI_FAILED state with error explanation and Regenerate.
- APPROVED is read-only. 409 shows "changed elsewhere, reload".
- All text rendered as plain text.

Acceptance criteria:
- Full flow works: create → edit → save → approve; regenerate after failure.
- Buttons never allow an action the backend would reject.

Verify: component tests for status-dependent actions and 409 handling; manual
end-to-end check of the full flow.

Commit: `feat: add message detail view with edit, approve and regenerate`

---

## F12 (Optional) Per-customer history

Build only if the developer asks for it.

Scope:
- New migration adding an index on `customer_email`.
- Optional `customerEmail` filter on `GET /api/messages`.
- "Other messages from this customer" link in the detail view.

Verify: integration test for the filter; manual check.

Commit: `feat: add per-customer message history`

---

## F13 Deployment and documentation

**Goal:** a working public deployment and a README a reviewer can follow.

Scope:
- Backend Dockerfile (multi-stage, Eclipse Temurin 25 base images, JVM memory flags
  for 512 MB).
- Render Postgres created with major version 17, matching local and test environments.
- Render configuration (web service + Postgres) and Vercel configuration (SPA routing).
- Add backend and frontend services to `docker-compose.yml`, so reviewers can run the
  whole app locally with one command.
- README: overview, architecture, local setup, env vars, deployment, key decisions
  (synchronous processing, single table, Basic auth, cold starts, interpretation of
  "history"), known limitations, and next steps from `CLAUDE.md`.
- Seed or demo data script if useful for reviewers.

Acceptance criteria:
- Deployed frontend logs in and completes the full flow against the deployed backend.
- `docker compose` alternative documented and working.

Verify: manual end-to-end check on the deployed URLs by the developer.

Commit: `chore: add deployment configuration and complete README`
