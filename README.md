# Rebeautifier

A calm, installable reading library for organizing thoughts as **Book → Chapter → Section → Notes**. Multi-user accounts with Owner / Editor / Viewer membership. Rich-text notes work without AI. Optional local AI refine via **Ollama** (backend-only).

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

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080) (API serves the built SPA).

Demo login (seeded on first boot):

- Username: `reader`
- Password: `password123`

Second demo user: `editor` / `password123` (editor on *Morning Pages*).

### Optional AI (Ollama)

```bash
docker compose --profile ai up --build
# then pull a small model into the ollama service, e.g.:
docker compose --profile ai exec ollama ollama pull llama3.2:1b
```

Default model: `llama3.2:1b` (4 GB RAM friendly). Override with `OLLAMA_MODEL` / `OLLAMA_BASE_URL`.

Without the AI profile, notes still work; the UI shows **AI offline**.

Stop:

```bash
docker compose down
```

Data persists in Docker volumes (`pgdata`, `covers`, `ollama`).

## Local development (non-Docker)

### 1. PostgreSQL

Create a database and user (example):

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

Then visit http://localhost:8080 → sign in as `reader` / `password123` → open **Morning Pages** → open a note → edit/save → use **Refine with AI** (shows offline if Ollama is absent) → restart Compose and confirm notes remain.

## API overview (`/api/v1`)

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `GET /auth/csrf` |
| Books | CRUD + `PUT/DELETE .../cover` + members |
| Chapters | list/create/reorder under book; patch/delete by id |
| Sections | list/create/reorder under chapter; patch/delete by id |
| Notes | list/create under book; get/patch/delete by id; reorder; draft/saved + content_version |
| AI | `GET /system/ai-status`; note refinements create/list; accept/reject/regenerate |
| Health | `GET /health`, `GET /health/ready` |

Auth uses an HTTP-only session cookie (`REBEAUTIFIER_SESSION`) plus CSRF (`XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header) for mutating requests.

## Project layout

```
src/main/java/com/rebeautifier/   # Spring Boot API
src/main/resources/db/migration/  # Flyway
frontend/                         # React + Vite SPA
Dockerfile
docker-compose.yml
```

## Phase status

Phase 4 (complete AI review workflow) — polished compare/accept/reject/regenerate UX, save-before-refine, AI status chip, multi-user refine ACL. Search/export come next.
