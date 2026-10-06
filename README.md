# Rebeautifier

A calm, installable reading library for organizing thoughts as **Book → Chapter → Section → Notes**. Multi-user accounts with Owner / Editor / Viewer membership. Rich-text notes work without AI. Optional local AI via Ollama arrives in later phases.

## Stack

- Java 17+, Spring Boot 3.4, Maven
- PostgreSQL 16 + Flyway
- Spring Security (cookie session)
- React + Vite + TypeScript SPA (`frontend/`)
- Docker Compose (`api` + `db`)

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

Stop:

```bash
docker compose down
```

Data persists in Docker volumes (`pgdata`, `covers`). Restart with `docker compose up` to confirm library data survives.

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

### 3. SPA (Vite)

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` to `:8080`.

### 4. Tests

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

Then visit http://localhost:8080 → sign in as `reader` / `password123` → open **Morning Pages** → browse chapters, sections, and seeded notes → open a note, edit with the rich-text editor, Save → create a draft note → restart Compose and confirm notes remain.

## API overview (`/api/v1`)

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `GET /auth/csrf` |
| Books | CRUD + `PUT/DELETE .../cover` + members |
| Chapters | list/create/reorder under book; patch/delete by id |
| Sections | list/create/reorder under chapter; patch/delete by id |
| Notes | list/create under book; get/patch/delete by id; reorder; draft/saved + content_version |
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

Phase 2 (notes without AI) — TipTap rich-text editor, draft/saved notes, autosave, ACL, sample seed notes. See project progress docs in the Cursor Project store.
