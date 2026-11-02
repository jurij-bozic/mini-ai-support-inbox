# CLAUDE.md

Guidance for Claude Code when working in this repository. Read this fully before making changes.

## Project overview

**Mini AI Support Inbox**: a small internal tool for handling customer messages.
An agent enters a customer message; the backend classifies it with an LLM
(QUESTION, COMPLAINT, PRAISE, OTHER), generates a draft reply, and stores both.
The agent can browse message history, edit the draft, and approve the final reply.

This is a take-home assignment for a full-stack position. Reviewers evaluate
**code quality, architecture, basic security, error handling, and how AI was used
during development**. UI design is explicitly not a priority. Prefer clear,
well-structured, tested code over extra features.

The original assignment text (Slovenian) is in `docs/ASSIGNMENT.md` and is the source of
the requirements. This file and `IMPLEMENTATION_PLAN.md` describe how they are implemented.
If they ever seem to contradict the assignment, stop and ask.

## Tech stack

- **Backend:** Spring Boot with Spring Web MVC, Spring Data JPA, Spring Security,
  Bean Validation, Flyway, Spring Boot Actuator, Bucket4j
- **Database:** PostgreSQL (Render Postgres in deployment, Docker locally)
- **LLM:** OpenRouter (OpenAI-compatible chat completions API), called via a thin
  hand-written client using Spring `RestClient`. Do not add Spring AI or other LLM SDKs.
- **Frontend:** React + TypeScript + Vite, React Router, plain `fetch` wrapper
- **Hosting:** Vercel (frontend), Render (backend + Postgres)

## Versions

These versions are fixed. Do not change a major or minor version without asking.

| Component | Version |
|---|---|
| Java | 25 (LTS), Eclipse Temurin |
| Spring Boot | 4.1.x, latest patch release (not 4.2, not milestones or snapshots) |
| Build tool | Maven, via the Maven Wrapper (`./mvnw`) |
| PostgreSQL | 17: `postgres:17` image locally and in Testcontainers; same major version on Render |
| Node.js | 24 LTS (pin in `.nvmrc` and `package.json` `engines`) |
| React | 19 |
| Vite, TypeScript | current stable at scaffold time, via `npm create vite` with the `react-ts` template |
| React Router | 7 |

After F1, record the exact resolved versions (Spring Boot patch, Vite, TypeScript) in `README.md`.

### Spring Boot 4 notes

Spring Boot 4 (on Spring Framework 7) differs from 3.x, and much example code online
and in model training data targets 3.x. To avoid mixing versions:
- Generate the backend from Spring Initializr (start.spring.io) with Boot 4.1, so starter
  names and the dependency setup are correct for Boot 4.
- Expect Boot 4 differences such as Jackson 3 (`tools.jackson.*` packages; the annotations
  stay in `com.fasterxml.jackson.annotation`), modularized starters and auto-configuration
  (e.g. Flyway has its own starter), and `@MockitoBean` instead of the removed `@MockBean`.
- If a class, starter, or property you expect does not resolve, check the Spring Boot 4.1
  reference documentation and migration guide. **Never downgrade a dependency to make
  code compile.**

## Prerequisites

- JDK 25
- Node.js 24 LTS with npm
- Docker with Compose v2, running (needed for local Postgres and for Testcontainers tests)
- Git

## Repository layout

This is a **single Git repository (monorepo)** containing both the backend and the
frontend. Do not create separate repositories, and never run `git init` in a subfolder
or leave a nested `.git` directory created by a scaffolding tool. Subfolders may have
their own `.gitignore` in addition to the root one.

```
/backend             Spring Boot application
/frontend            React application
docker-compose.yml   Local Postgres (+ optional backend/frontend)
README.md            Setup, architecture, decisions, trade-offs
AI_USAGE.md          Written by the developer; do not edit unless explicitly asked
CLAUDE.md            This file
IMPLEMENTATION_PLAN.md  Feature order and approval process
docs/ASSIGNMENT.md   Original assignment text; read-only source of requirements
```

## Commands

```bash
# Local database
docker compose up -d postgres

# Backend (from /backend)
./mvnw spring-boot:run
./mvnw test                 # unit + integration tests (Testcontainers needs Docker)
./mvnw verify

# Frontend (from /frontend)
npm install
npm run dev
npm run build
npm run lint
npm test
```

Always run the relevant tests before declaring a task done.

## Backend architecture

Package by feature, not by layer:

```
com.example.supportinbox
├── message/   MessageController, MessageService, MessageRepository,
│              Message (entity), MessageStatus, Category, dto/
├── ai/        AiAssistant (interface), OpenRouterAiAssistant, Prompts,
│              ClassificationResult, AiException, AiErrorCode
├── auth/      SecurityConfig, ProblemDetailAuthEntryPoint, AuthController (/api/auth/me)
├── common/    GlobalExceptionHandler, RateLimitFilter, CorsConfig
└── config/    @ConfigurationProperties records (AiProperties, AdminProperties, ...)
```

Rules:
- Controllers are thin: validate input, call the service, map to DTOs. No business logic.
- Use Java records for DTOs. Never expose JPA entities from controllers.
- Map entities to DTOs explicitly (no mapping libraries).
- All configuration via `@ConfigurationProperties` records bound from environment variables.
  No hardcoded secrets, URLs, or model names.
- Constructor injection only. No field injection.
- The `AiAssistant` interface is the only thing the message package knows about AI.
  It must be easy to mock in tests and to swap providers.

## Data model

Single table `messages` (1:1 message/reply; no separate replies table).

| Column | Notes |
|---|---|
| `id` | UUID, `gen_random_uuid()` |
| `customer_name`, `customer_email` | optional |
| `content` | required, 1–5000 chars (also enforced by DB CHECK) |
| `category` | QUESTION / COMPLAINT / PRAISE / OTHER, nullable until classified |
| `status` | PENDING_AI / DRAFT_READY / AI_FAILED / APPROVED |
| `ai_draft` | original AI suggestion, never overwritten by agent edits |
| `final_reply` | agent's edited text |
| `ai_error_code` | short code (TIMEOUT, RATE_LIMITED, PROVIDER_ERROR, ...), never raw provider messages |
| `created_at`, `updated_at`, `approved_at` | `TIMESTAMPTZ` |
| `version` | optimistic locking via JPA `@Version` |

Indexes on `created_at DESC` and `status`.

Database rules:
- Schema changes only through Flyway migrations in `src/main/resources/db/migration`.
- **Never edit a migration that has already been committed.** Add a new one.
- `spring.jpa.hibernate.ddl-auto=validate`.
- Enforce valid values with DB CHECK constraints as well as in Java.

## Status lifecycle

```
PENDING_AI ──► DRAFT_READY ──► APPROVED (final, read-only)
    │             ▲   │
    ▼             │   └─ edit reply (stays DRAFT_READY)
AI_FAILED ── regenerate
```

- `regenerate` is allowed from AI_FAILED and DRAFT_READY.
- Editing and approving are allowed only from DRAFT_READY.
- APPROVED is final. Any invalid transition throws a domain exception mapped to **409**.
- Keep transition rules in one place (the entity or the service), not scattered.

## Processing flow (synchronous request-response)

`POST /api/messages` runs the whole pipeline before responding:

1. Validate input, save message as PENDING_AI (short transaction, committed).
2. Classify with the classification model (structured JSON output).
3. Generate the draft with the reply model, passing the category to steer tone.
4. Save DRAFT_READY with category and draft (short transaction).
   On any AI failure, save AI_FAILED with an error code instead.

Critical rules:
- **No database transaction may be open during an LLM call.** Put the short
  transactional steps in a separate bean (watch out for `@Transactional`
  self-invocation, which silently does nothing).
- An AI failure still returns **201**, because the message was saved. The body shows
  `status: AI_FAILED`; the frontend offers Regenerate.
- Design so the AI step could later move to a background worker without schema changes.
- Enable virtual threads: `spring.threads.virtual.enabled=true`.

## AI integration rules

- Two configurable models: `AI_MODEL_CLASSIFY` (cheap/fast) and `AI_MODEL_REPLY` (higher quality).
- Prompts live in `ai/Prompts.java` as constants or text blocks, not inline in client code.
- **Classification output format.** The model must return exactly one JSON object and
  nothing else:

  ```json
  {"category": "QUESTION"}
  ```

  `category` is one of `QUESTION`, `COMPLAINT`, `PRAISE`, `OTHER`. Request it with
  `response_format` of type `json_schema` using this schema:

  ```json
  {
    "type": "object",
    "properties": {
      "category": { "type": "string", "enum": ["QUESTION", "COMPLAINT", "PRAISE", "OTHER"] }
    },
    "required": ["category"],
    "additionalProperties": false
  }
  ```

  If the configured model or provider does not support `json_schema`, state the exact
  format in the system prompt instead; the parser handles both cases.
- **Classification parsing:** trim whitespace, strip Markdown code fences if present,
  parse the JSON, read `category`, trim and uppercase it, and map it to the `Category` enum.
  Ignore any extra fields. Invalid JSON, a missing field, or an unknown value falls back to
  OTHER and is logged at WARN with the message ID (never the message content).
  A transport or provider failure during classification is AI_FAILED (do not draft).
- **Draft output format:** plain text containing only the reply body. No JSON, no subject
  line, no Markdown, no placeholders such as `[Name]`. If the customer's name is unknown,
  use a neutral greeting. The draft must not invent facts (order numbers, refunds, policies,
  deadlines); where information is missing, it says the team will follow up.
- **Prompt injection:** customer text is untrusted data. Wrap it in clear delimiters
  (e.g. `<customer_message>...</customer_message>`) and instruct the model to treat it
  as content to analyze, never as instructions. Never put customer text in the system prompt.
- Reply in the **same language as the customer message** (expect Slovenian and English).
- Always set `max_tokens` on requests.
- Timeouts: connect 5s, read 15s per call. Frontend request timeout: 60s.
  Backend timeouts must always fire before the frontend gives up.
- Retry once with short backoff on HTTP 429 and 5xx only. Do not retry timeouts,
  401, 402, or 4xx validation errors.
- Map failures to `AiErrorCode`: TIMEOUT, RATE_LIMITED, AUTH_ERROR (401),
  INSUFFICIENT_CREDITS (402), PROVIDER_ERROR, INVALID_RESPONSE.
- Never log the API key. Never return provider error bodies to the client.

## REST API

```
GET    /actuator/health                          public (keep-alive ping)
GET    /api/auth/me                              auth → 200 {username} / 401
POST   /api/messages                             auth, rate limited → 201
GET    /api/messages?status=&category=&page=&size=   auth → paginated list
GET    /api/messages/{id}                        auth → 200 / 404
PUT    /api/messages/{id}/reply                  auth, body {finalReply, version} → 200 / 409
POST   /api/messages/{id}/approve                auth, body {finalReply, version} → 200 / 409
POST   /api/messages/{id}/regenerate             auth, rate limited → 200 / 409
```

- Pagination: default size 20, max 100. Sort by `created_at DESC`.
- Version conflicts (`OptimisticLockingFailureException`) map to 409.

## Error handling

- One `@RestControllerAdvice` returning Spring `ProblemDetail` (RFC 9457) for every error.
- 400 validation errors include field-level details.
- 404 for unknown IDs, 409 for invalid transitions and version conflicts,
  429 from the rate limiter, 401 from security, 500 for unexpected errors.
- 500 responses must never include stack traces or exception messages.
- Never swallow exceptions silently. Log unexpected errors at ERROR with the message ID.

## Security

- Spring Security, HTTP Basic, **stateless**, single in-memory admin user.
- Credentials from env: `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` (`{bcrypt}...`). Never plaintext.
- Custom `AuthenticationEntryPoint` returns a 401 `ProblemDetail` **without** the
  `WWW-Authenticate` header (prevents the browser's native login popup).
- CSRF disabled, with a code comment explaining why: no cookies, credentials are sent
  only in an explicitly set `Authorization` header.
- CORS: allow only origins from `CORS_ALLOWED_ORIGINS` (Vercel domain, localhost in dev).
  Allow the `Authorization` and `Content-Type` headers. Permit OPTIONS preflight.
- Rate limiting (Bucket4j, in-memory) on `POST /api/messages` and `/regenerate`:
  one global bucket for AI endpoints, configurable via env. Purpose: cap LLM cost.
- Bean Validation on all request bodies, with explicit max lengths.
- **Logging:** never log message content, replies, credentials, or the API key at INFO
  or above. Log IDs, status changes, error codes, and latency.

## Environment variables

```
DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
OPENROUTER_API_KEY
AI_BASE_URL=https://openrouter.ai/api/v1
AI_MODEL_CLASSIFY, AI_MODEL_REPLY
ADMIN_USERNAME, ADMIN_PASSWORD_HASH
CORS_ALLOWED_ORIGINS
RATE_LIMIT_AI_PER_MINUTE
VITE_API_BASE_URL            (frontend)
```

Keep `.env.example` files with **placeholders only**. Real `.env` files are gitignored.

## Frontend

- Views: login, message list (status/category badges, filters, pagination),
  new message form, message detail (original message, category, read-only AI draft,
  editable final reply, Save / Approve / Regenerate).
- One API module (`src/api/`) wrapping `fetch`: base URL from `VITE_API_BASE_URL`,
  adds the `Authorization` header, applies a 60s timeout via `AbortController`,
  parses `ProblemDetail` errors into a typed error.
- On any 401: clear credentials and return to the login view.
- Credentials are held **in memory only** (React context). No `localStorage` or
  `sessionStorage`. A page refresh logging the user out is accepted and intentional.
- Render AI output and customer text as plain text. Never `dangerouslySetInnerHTML`.
- Clear loading states: AI processing takes several seconds, and the first request
  after idle may hit a backend cold start ("server is waking up" message).
- Show 409 conflicts clearly ("This message was changed elsewhere, reload").
- Keep styling minimal and functional.

## Testing

- **Unit tests** for `MessageService` with a mocked `AiAssistant`: happy path,
  failure during classification → AI_FAILED without drafting, failure during drafting →
  AI_FAILED, invalid transitions → domain exception, regenerate from AI_FAILED.
- **Unit tests** for the OpenRouter client against a mock HTTP server
  (e.g. `MockRestServiceServer`): response parsing, error-code mapping, retry behavior.
- **Integration tests** with Testcontainers Postgres + MockMvc: create → edit → approve flow,
  validation errors, 401 without credentials, optimistic lock conflict.
- Tests must never call the real LLM API.
- Frontend: a few focused tests for the API wrapper and the detail view are enough.

## Working rules for Claude Code

- **Follow `IMPLEMENTATION_PLAN.md`, including its approval process.** Read it at the
  start of every session, check its progress checklist, and work only on the next
  unapproved feature. Never proceed to the next feature without the developer's
  explicit approval.
- Work in **small, reviewable steps**. One coherent change per step, so it can be
  committed on its own with a clear message.
- For anything touching several files or making an architectural choice,
  **propose a short plan first** and wait for approval.
- **Do not add dependencies** beyond the stack above without asking.
- If the spec in this file is ambiguous or seems wrong, ask instead of guessing.
- Do not edit `AI_USAGE.md` or `docs/ASSIGNMENT.md` unless explicitly asked.
- Do not weaken security, validation, or error handling to make something work quickly.
  If a rule here blocks progress, say so.
- Update `README.md` when setup steps, env vars, or architecture decisions change.

## Implementation order

The build order, feature scope, and approval process are defined in
`IMPLEMENTATION_PLAN.md`. That file is the single source of truth for what to build next.

## Out of scope (mention in README as next steps)

- Async/job-based AI processing with polling or SSE
- Multiple agents, roles, SSO/OIDC, `approved_by` audit
- Actually sending replies to customers (email integration)
- Draft history / multiple drafts per message
- Distributed rate limiting
