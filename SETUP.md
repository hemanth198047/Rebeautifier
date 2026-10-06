# Rebeautifier setup

Self-hosted install. No cloud accounts required.

## One-command install (Docker Compose)

Requirements: Docker Engine + Docker Compose v2.

On Windows, start **Docker Desktop** first and wait until it shows Running before using `start.bat`.

**Linux / macOS**

```bash
git clone https://github.com/hemanth198047/Rebeautifier.git
cd Rebeautifier
./start.sh
```

**Windows** (Docker Desktop running)

```bat
git clone https://github.com/hemanth198047/Rebeautifier.git
cd Rebeautifier
start.bat
```

Open http://localhost:8080

The start scripts copy `.env.example` → `.env` when needed, run `docker compose up --build -d`, and wait until `/api/v1/health/ready` is green.

| Script | Platform | Notes |
| --- | --- | --- |
| `start.sh` / `stop.sh` | Linux, macOS | `--ai`, `--pull-model`, `--host-gateway`, `--purge` |
| `start.bat` / `stop.bat` | Windows CMD | `--ai`, `--pull-model`, `--host-gateway`, `--purge` |
| `start.ps1` / `stop.ps1` | Windows PowerShell | `-Ai`, `-PullModel`, `-HostGateway`, `-Purge` |

Demo users (when `SEED_DEMO_DATA=true`, the default for Compose):

| User | Password | Notes |
| --- | --- | --- |
| `reader` | `password123` | Owns *Morning Pages* |
| `editor` | `password123` | Editor on *Morning Pages* |

Stop:

```bash
./stop.sh
# Windows: stop.bat
```

Data persists in volumes: `pgdata` (Postgres), `covers` (book cover files), `ollama` (models when AI profile is used).

### Optional local AI

```bash
./start.sh --ai --pull-model
# Windows: start.bat --ai --pull-model
```

Default model `llama3.2:1b` targets ~4 GB RAM hosts with concurrency `1`. Notes still work when Ollama is absent — Settings and the AI chip show offline guidance.

### Production-ish Compose tips

- Copy `.env.example` → `.env` and set a strong `POSTGRES_PASSWORD`.
- For non-demo installs: `SEED_DEMO_DATA=false` and `SPRING_PROFILES_ACTIVE=prod`.
- Behind HTTPS: `SESSION_COOKIE_SECURE=true`.
- Postgres and Ollama are not published to the host by default (API uses the Compose network). Use `docker compose exec db pg_dump …` for dumps. The public surface is the API port (`8080` by default).
- Health probes: `GET /api/v1/health` (liveness) and `GET /api/v1/health/ready` (readiness; HTTP 503 if DB is down).

### Nested Docker / host-gateway override

If container-to-container networking is broken in your environment:

```bash
docker compose -f docker-compose.yml -f docker-compose.host-gateway.yml up --build
```

## Non-Docker local run

Requirements: JDK 17+, Node 20+, PostgreSQL 16.

```bash
# 1. Database
sudo -u postgres psql -c "CREATE USER rebeautifier WITH PASSWORD 'rebeautifier';"
sudo -u postgres psql -c "CREATE DATABASE rebeautifier OWNER rebeautifier;"
sudo -u postgres psql -c "CREATE DATABASE rebeautifier_test OWNER rebeautifier;"

# 2. API
./mvnw spring-boot:run

# 3. SPA (another terminal)
cd frontend
npm install
npm run dev
```

- API: http://localhost:8080  
- Vite: http://localhost:5173 (proxies `/api`)

Optional Ollama on the host:

```bash
# install from https://ollama.com
ollama pull llama3.2:1b
```

Env knobs (API process):

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/rebeautifier` | JDBC URL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `rebeautifier` | DB credentials |
| `COVER_STORAGE_PATH` | `./data/covers` | Cover file directory |
| `SEED_DEMO_DATA` | `true` | Seed demo users/books |
| `CORS_ORIGINS` | Vite origins | Allowed browser origins |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama HTTP API |
| `OLLAMA_MODEL` | `llama3.2:1b` | Default refine model |
| `OLLAMA_MAX_CONCURRENT` | `1` | In-flight AI jobs |
| `APP_VERSION` | `0.6.0` | Reported in health payloads |
| `SESSION_COOKIE_SECURE` | `false` | Secure session cookie (HTTPS) |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `30` | Sliding-window auth login/register limit (in-memory per API process) |
| `RATE_LIMIT_AI_PER_MINUTE` | `20` | Sliding-window AI refine limit (in-memory per API process) |

## Backup and recovery

### In-app (per book)

Settings → **Backup**, or `/backup`:

- Export Markdown (readable) or JSON (`rebeautifier.book-backup` v1)
- Import JSON restores as a **new owned book**

### Host-level (full install)

```bash
# Postgres dump (Compose — no host DB port by default)
docker compose exec -T db pg_dump -U rebeautifier rebeautifier > rebeautifier-$(date +%F).sql

# Covers volume (example: copy from named volume)
docker run --rm -v rebeautifier_covers:/from -v "$PWD":/to alpine \
  tar czf /to/covers-$(date +%F).tgz -C /from .
```

Restore: recreate the stack, restore the SQL dump into `db`, restore covers into the `covers` volume, then optionally re-import JSON book backups for selective recovery.

## Smoke script

With the API listening on `:8080`:

```bash
./scripts/smoke-check.sh
```

Checks liveness, readiness (expects HTTP 200), SPA shell, and AI status endpoint reachability (offline is OK).
