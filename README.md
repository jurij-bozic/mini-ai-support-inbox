# Mini AI Support Inbox

A small internal tool for handling customer messages. An agent enters a customer
message; the backend classifies it with an LLM (question, complaint, praise, other),
drafts a reply, and stores both. The agent can browse the message history, edit the
draft, and approve the final reply.

Architecture, conventions and the build order are described in [CLAUDE.md](CLAUDE.md)
and [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). The original assignment is in
[docs/ASSIGNMENT.md](docs/ASSIGNMENT.md).

> Work in progress: only the project skeleton exists so far (feature F1).

## Prerequisites

- JDK 25 (Eclipse Temurin)
- Node.js 24 LTS with npm (`nvm use` picks up `frontend/.nvmrc`)
- Docker with Compose v2, running (local Postgres and Testcontainers tests)
- Git

## Local setup

```bash
# 1. Database (Postgres 17)
docker compose up -d postgres

# 2. Backend, from /backend (http://localhost:8080)
#    Needs the admin account (see "Admin account" below)
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD_HASH='{bcrypt}...'
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health   # {"status":"UP",...}

# 3. Frontend, from /frontend (http://localhost:5173)
npm install
npm run dev
```

The backend database defaults match `docker-compose.yml`. The admin account has no default
and the backend refuses to start without it. To override a setting, export the environment variable listed in
`backend/.env.example` or `frontend/.env.example`. Real `.env` files are gitignored.

## Admin account

The API uses HTTP Basic with a single admin user. The password is supplied only as a bcrypt
hash in `ADMIN_PASSWORD_HASH`, prefixed with `{bcrypt}`. Generate one with, for example:

```bash
echo "{bcrypt}$(htpasswd -nbBC 10 x 'your-password' | cut -d: -f2)"
```

`CORS_ALLOWED_ORIGINS` (comma-separated, default `http://localhost:5173`) lists the frontend
origins the browser may call the API from.

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
