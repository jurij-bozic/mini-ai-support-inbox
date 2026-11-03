# Mini AI Support Inbox

A small internal tool for handling customer messages. An user (agent) enters a customer
message; the backend classifies it with an LLM (question, complaint, praise, other),
drafts a reply, and stores both. The agent can browse the message history, edit the
draft, and approve the final reply.

The original assignment is in [docs/ASSIGNMENT.md](docs/ASSIGNMENT.md) (Slovenian). Detailed
conventions and the build order are in [CLAUDE.md](CLAUDE.md) and
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). How AI was used during development is described
in [AI_USAGE.md](AI_USAGE.md).

## Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Local setup](#local-setup)
- [Configuration](#configuration)
- [Deployment](#deployment)
- [Key decisions](#key-decisions)
- [Known limitations and next steps](#known-limitations-and-next-steps)
- [Checks](#checks)
- [Resolved versions](#resolved-versions)

## Architecture

```
React SPA (Vercel)  ──HTTP Basic──►  Spring Boot API (Render)  ──►  PostgreSQL 17 (Render)
                                              │
                                              └──►  OpenRouter (classification + reply drafting)
```

**Backend** (`/backend`, Spring Boot, packaged by feature):

| Package | Responsibility |
|---|---|
| `message` | REST controller, service, short transactional steps, entity, DTOs, status lifecycle |
| `ai` | `AiAssistant` interface, OpenRouter client (`RestClient`), prompts, response parsing, error codes |
| `auth` | Stateless HTTP Basic security, 401 `ProblemDetail` entry point, `/api/auth/me` |
| `common` | Global exception handler (RFC 9457 `ProblemDetail`), rate-limit filter, CORS |
| `config` | `@ConfigurationProperties` records bound from environment variables |

The `message` package only knows the `AiAssistant` interface, so the provider can be swapped or
mocked. Tests never call the real LLM.

**Frontend** (`/frontend`, React 19 + TypeScript + Vite, React Router): login, message list with
filters and pagination, new message form, and a detail view to edit, approve or regenerate. One
`src/api/` module wraps `fetch` (base URL, `Authorization` header, 60 s timeout, typed
`ProblemDetail` errors).

**Data model:** a single `messages` table (one message, one reply), managed by Flyway migrations.
Status lifecycle:

```
PENDING_AI ──► DRAFT_READY ──► APPROVED (final, read-only)
    │             ▲   │
    ▼             │   └─ edit reply (stays DRAFT_READY)
AI_FAILED ── regenerate
```

Invalid transitions return 409, as do stale edits (optimistic locking with a `version` column).

## Prerequisites

- JDK 25 (Eclipse Temurin)
- Node.js 24 LTS with npm (`nvm use` picks up `frontend/.nvmrc`)
- Docker with Compose v2, running (local Postgres, the compose option and Testcontainers tests)
- Git

## Local setup

Two ways to run the backend locally. Both need Postgres, an admin account hash and an OpenRouter
key (see [Configuration](#configuration)). The frontend always runs with `npm run dev`.

### Option A: backend from source

```bash
# 1. Database (Postgres 17)
docker compose up -d postgres

# 2. Backend, from /backend (http://localhost:8080)
#    Keep real values in the gitignored backend/.env (see backend/.env.example) and load them:
set -a; source .env; set +a
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health   # {"status":"UP",...}

# 3. Frontend, from /frontend (http://localhost:5173)
cp .env.example .env     # VITE_API_BASE_URL=http://localhost:8080
npm install
npm run dev
```

### Option B: backend in Docker Compose

```bash
cp .env.example .env     # in the repository root; fill in the placeholders
docker compose up -d --build     # Postgres 17 + backend on http://localhost:8080
cd frontend && npm install && npm run dev
```

The compose backend uses the compose Postgres automatically. In the root `.env`, **wrap
`ADMIN_PASSWORD_HASH` in single quotes**: Compose treats `$` as variable interpolation and would
otherwise corrupt the bcrypt hash. If a required variable is missing, the backend container
exits at startup (the app refuses to boot without its admin account, API key and model IDs).
Stop with `docker compose down` (add `-v` to also delete the database volume).

Spring Boot does not read `.env` files itself, which is why Option A sources the file into the
shell.

## Configuration

All configuration is read from environment variables. Real values live in gitignored `.env`
files; the `.env.example` files contain placeholders only.

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `DATABASE_URL` | backend | `jdbc:postgresql://localhost:5432/support_inbox` | JDBC URL (must start with `jdbc:postgresql://`) |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | backend | `support_inbox` | Database credentials |
| `ADMIN_USERNAME` | backend | none (required) | Single admin user |
| `ADMIN_PASSWORD_HASH` | backend | none (required) | `{bcrypt}...` hash, never plaintext |
| `OPENROUTER_API_KEY` | backend | none (required) | OpenRouter API key |
| `AI_BASE_URL` | backend | `https://openrouter.ai/api/v1` | OpenAI-compatible API base URL |
| `AI_MODEL_CLASSIFY` | backend | none (required) | Cheap/fast model for classification |
| `AI_MODEL_REPLY` | backend | none (required) | Higher quality model for drafts |
| `CORS_ALLOWED_ORIGINS` | backend | `http://localhost:5173` | Comma-separated allowed frontend origins |
| `RATE_LIMIT_AI_PER_MINUTE` | backend | `10` | Global cap on AI-triggering requests |
| `PORT` | backend | `8080` | HTTP port (Render sets this) |
| `VITE_API_BASE_URL` | frontend | empty | Backend base URL, no trailing slash. Read at **build time** |

The backend refuses to start without the admin account, API key and model IDs.

**Admin password hash.** Generate one with, for example:

```bash
echo "{bcrypt}$(htpasswd -nbBC 10 x 'your-password' | cut -d: -f2)"
```

**AI provider.** `AI_MODEL_CLASSIFY` and `AI_MODEL_REPLY` are OpenRouter model IDs. Classification
requests a JSON schema response and falls back to `OTHER` on unparseable output; transport and
provider failures save the message as `AI_FAILED`, and the agent can regenerate.

**Rate limiting.** `POST /api/messages` and `POST /api/messages/{id}/regenerate` trigger paid LLM
calls, so they share one global in-memory bucket (Bucket4j). Beyond the limit the API returns 429
with a `Retry-After` header. Other endpoints are not limited.

## Deployment

The backend and database run on Render, the frontend on Vercel. The configuration files are
[render.yaml](render.yaml), [backend/Dockerfile](backend/Dockerfile) and
[frontend/vercel.json](frontend/vercel.json).

> The local Docker image and the compose setup have been built and run. The Render and Vercel
> steps below follow their documented behaviour but have not been verified against live accounts
> yet; treat them as a checklist and fix this section if a step differs.

### Backend and database (Render)

1. In Render, create a **Blueprint** from this repository. It reads `render.yaml` and creates the
   `support-inbox-backend` web service (Docker, health check `/actuator/health`) and a
   `support-inbox-db` Postgres **17** database in the same region.
2. Fill in the variables marked `sync: false`:
   - `DATABASE_URL`: the JDBC form with the database's **internal** host, i.e.
     `jdbc:postgresql://<internal-host>:5432/support_inbox`. Render shows a `postgres://` URL,
     which the JDBC driver cannot read, so convert it by hand. `DATABASE_USERNAME` and
     `DATABASE_PASSWORD` are wired from the database automatically.
   - `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH`, `OPENROUTER_API_KEY`, `AI_MODEL_CLASSIFY`,
     `AI_MODEL_REPLY`.
   - `CORS_ALLOWED_ORIGINS`: the exact Vercel origin, e.g. `https://your-app.vercel.app`
     (no trailing slash, no path).
3. Deploy. Flyway creates the schema on first start. Check `https://<service>.onrender.com/actuator/health`.

The image is tuned for a 512 MB instance (`MaxRAMPercentage=70`, serial GC, C1 compiler only).
Tests are not run during the image build because the integration tests need Docker; run
`./mvnw verify` before deploying.

### Frontend (Vercel)

1. Import the repository in Vercel with **Root Directory** `frontend` (framework preset: Vite).
2. Set `VITE_API_BASE_URL` to the Render service URL (no trailing slash) **before** the first build,
   since Vite inlines it at build time. Changing it later requires a redeploy.
3. `vercel.json` rewrites all routes to `index.html` so deep links such as `/messages/<id>` survive
   a page refresh.

### Cold starts

Free Render instances sleep when idle, so the first request can take up to a minute. The frontend
shows a "server is waking up" message for slow responses. To keep the service warm, point an
external pinger (e.g. UptimeRobot) at the public `/actuator/health` endpoint. The backend's
own timeouts (5 s connect, 15 s read per LLM call, one retry on 429/5xx) fire before the
frontend's 60 s request timeout.

## Key decisions

- **Synchronous processing.** `POST /api/messages` classifies and drafts before responding. It is
  the simplest design that satisfies the assignment, with no queue or polling. No database
  transaction is open during an LLM call: short transactional steps live in a separate bean. An AI
  failure still returns 201 with `status: AI_FAILED`, and the original message is never lost. The
  schema and service split would allow moving the AI step to a background worker later.
- **Single table.** One message has one reply, so `messages` holds both. `ai_draft` is never
  overwritten; the agent's edits go to `final_reply`.
- **HTTP Basic, stateless, one admin user.** Credentials come from the environment (bcrypt hash)
  and the frontend keeps them in memory only, so a refresh logs out. CSRF protection is disabled
  because there are no cookies: credentials are sent only in an explicit `Authorization` header.
  The 401 response omits `WWW-Authenticate` so browsers show no native login popup.
- **Thin hand-written OpenRouter client.** A `RestClient` instead of an LLM SDK keeps dependencies
  small and the provider swappable behind `AiAssistant`.
- **Prompt injection.** Customer text is untrusted: it is wrapped in delimiters in the user
  message, never placed in the system prompt, and replies must not invent facts.
- **Errors.** One `@RestControllerAdvice` returns RFC 9457 `ProblemDetail` for every error;
  500 responses carry no stack traces or exception messages, and provider error bodies are never
  returned to the client or logged with the API key. Message content is not logged at INFO or above.
- **Interpretation of "history".** The assignment asks for an overview of message history. This is
  implemented as a paginated list of all messages, newest first, filterable by status, category and
  customer email, plus an "other messages from this customer" link on the detail view.
- **Optimistic locking.** Concurrent edits are detected with a `version` column and return 409; the
  UI asks the agent to reload.

## Known limitations and next steps

- Single admin account; no roles, SSO/OIDC or `approved_by` audit trail.
- Credentials are held in memory, so a page refresh requires logging in again.
- The rate limiter is in-memory and per instance, not distributed.
- AI processing is synchronous; async/job-based processing with polling or SSE would help under load.
- If the backend restarts while a message is being processed, it stays in `PENDING_AI`. There is no
  background recovery job; instead, such a message can be regenerated once it has not been updated for
  2 minutes (longer than the worst-case AI run of about 85 s). The 2-minute threshold is a code constant
  (`Message.PENDING_STALE_AFTER`), not an environment variable.
- Approved replies are not sent to customers (no email integration).
- One draft per message; no draft history.
- The Render/Vercel deployment steps are documented but should be re-verified after the first
  real deployment.

## Checks

```bash
# Backend (from /backend); integration tests need Docker running
./mvnw verify

# Frontend (from /frontend)
npm run build
npm run lint
npm test
```

## Resolved versions

| Component | Version |
|---|---|
| Java | Temurin 25.0.5 (LTS) |
| Spring Boot | 4.1.2 |
| Maven (via wrapper) | 3.10.0 |
| PostgreSQL | 17 |
| Node.js | 24 LTS |
| React | 19.3.1 |
| React Router | 7.18.5 |
| Vite | 8.3.5 |
| TypeScript | 7.0.2 |
| Vitest | 5.1.0 |
