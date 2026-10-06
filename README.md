# Rebeautifier

A calm, installable reading library for organizing thoughts as **Book → Chapter → Section → Notes**. Multi-user accounts with Owner / Editor / Viewer membership. Rich-text notes work without AI. Optional local AI refine via **Ollama** (backend-only).

**Version:** 0.7.0 · Full setup: [`SETUP.md`](./SETUP.md) · Acceptance: [`ACCEPTANCE.md`](./ACCEPTANCE.md)

## Stack

- Java 17+, Spring Boot 3.4, Maven
- PostgreSQL 16 + Flyway
- Spring Security (cookie session)
- React + Vite + TypeScript SPA (`frontend/`)
- TipTap rich-text notes
- Docker Compose (`api` + `db`, optional `ollama` profile)
- Ollama HTTP API (local only)

## Quick start (Docker Compose)

Requirements: Docker and Docker Compose.

**Linux / macOS**

```bash
./start.sh                 # builds and starts db + api (SPA on :8080)
./start.sh --ai --pull-model   # optional local Ollama
./stop.sh                  # stop (keeps volumes)
./stop.sh --purge          # stop and delete volumes
```

**Windows**

```bat
start.bat
start.bat --ai --pull-model
stop.bat
stop.bat --purge
```

PowerShell alternatives: `.\start.ps1`, `.\stop.ps1` (flags: `-Ai`, `-PullModel`, `-HostGateway`, `-Purge`).

Nested Docker / broken bridge networking:

```bash
./start.sh --host-gateway
# Windows: start.bat --host-gateway
```

Or raw Compose:

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080) (API serves the built SPA).

Demo login (seeded on first boot):

- Username: `reader`
- Password: `password123`

Second demo user: `editor` / `password123` (editor on *Morning Pages*).

Optional env file: copy [`.env.example`](./.env.example) to `.env` (created automatically by `start.sh` / `start.bat` if missing).

### Optional AI (Ollama)

```bash
./start.sh --ai --pull-model
# or:
docker compose --profile ai up --build
docker compose --profile ai exec ollama ollama pull llama3.2:1b
```

Default model: `llama3.2:1b` (4 GB RAM friendly). Override with `OLLAMA_MODEL` / `OLLAMA_BASE_URL`.

Without the AI profile, notes still work; the UI shows **AI offline** (see **Settings**).

Stop:

```bash
./stop.sh
# or: docker compose down
```

Data persists in Docker volumes (`pgdata`, `covers`, `ollama`).

Health probes for orchestration:

- Liveness: `GET /api/v1/health`
- Readiness: `GET /api/v1/health/ready` (HTTP 503 if database is down)

Quick smoke (API must be up):

```bash
./scripts/smoke-check.sh
```

## Local development (non-Docker)

See [`SETUP.md`](./SETUP.md) for the full path. Short version:

### 1. PostgreSQL

```bash
sudo -u postgres psql -c "CREATE USER rebeautifier WITH PASSWORD 'rebeautifier';"
sudo -u postgres psql -c "CREATE DATABASE rebeautifier OWNER rebeautifier;"
```

### 2. API

```bash
./mvnw spring-boot:run
```

API listens on [http://localhost:8080](http://localhost:8080).

Env knobs: `OLLAMA_BASE_URL` (default `http://localhost:11434`), `OLLAMA_MODEL` (default `llama3.2:1b`), `OLLAMA_MAX_CONCURRENT` (default `1`).

### 3. SPA (Vite)

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` to `:8080`.

### 4. Optional Ollama

Install from [ollama.com](https://ollama.com), then:

```bash
ollama pull llama3.2:1b
```

### 5. Tests

```bash
# create once
sudo -u postgres psql -c "CREATE DATABASE rebeautifier_test OWNER rebeautifier;"

./mvnw test
```

```bash
cd frontend && npm run build
```

## Beginner path (exact commands)

From a clean clone with Docker installed:

```bash
git clone https://github.com/hemanth198047/Rebeautifier.git
cd Rebeautifier
docker compose up --build
```

Then visit http://localhost:8080 → sign in as `reader` / `password123` → open **Morning Pages** → open a note → edit/save → use **Refine with AI** (shows offline if Ollama is absent) → open **Settings** for install/AI status → restart Compose and confirm notes remain.

## Backup

- In-app: **Backup** page — Markdown or versioned JSON per book; JSON import creates a new owned book.
- Host-level: `pg_dump` + covers volume — see [`SETUP.md`](./SETUP.md#backup-and-recovery).

## API overview (`/api/v1`)

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET/PATCH /auth/me`, `POST /auth/change-password`, `GET /auth/csrf` |
| Books | CRUD + `PUT/DELETE .../cover` + members |
| Chapters | list/create/reorder under book; patch/delete by id |
| Sections | list/create/reorder under chapter; patch/delete by id |
| Notes | list/create under book; get/patch/delete by id; reorder; draft/saved + content_version |
| AI | `GET /system/ai-status`; note refinements create/list; accept/reject/regenerate |
| Limits | `GET /system/rate-limits` (auth + AI sliding windows; in-memory per process) |
| Search | `GET /search?q=` ACL-scoped |
| Backup | `GET /books/{id}/export?format=json\|markdown`; `POST /books/import`; `PUT /books/{id}/import` (owner replace) |
| Members | invite / role patch / remove; `POST /books/{id}/transfer` ownership |
| Health | `GET /health`, `GET /health/ready` |

Auth uses an HTTP-only session cookie (`REBEAUTIFIER_SESSION`) plus CSRF (`XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header) for mutating requests.

## Project layout

```
src/main/java/com/rebeautifier/   # Spring Boot API
src/main/resources/db/migration/  # Flyway
frontend/                         # React + Vite SPA
Dockerfile
docker-compose.yml
docker-compose.host-gateway.yml   # optional nested-Docker override
SETUP.md
ACCEPTANCE.md
scripts/smoke-check.sh
.env.example
```

## Phase status

Phase 11 (rate-limit transparency) — Settings rate-limit status, Retry-After UX, frontend unit tests.
