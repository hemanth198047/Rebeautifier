# Rebeautifier — end-to-end acceptance checklist

Use after a clean install (Compose recommended). Mark each item when verified.

## Install paths

- [ ] `docker compose up --build` starts `db` + `api` without AI
- [ ] http://localhost:8080 serves the SPA
- [ ] Demo login `reader` / `password123` works (when seeding is on)
- [ ] `docker compose --profile ai up --build` starts Ollama; pull `llama3.2:1b` succeeds
- [ ] Non-Docker path in `SETUP.md` runs API + Vite against local Postgres
- [ ] `./scripts/smoke-check.sh` exits 0 against a running install

## Health and packaging

- [ ] `GET /api/v1/health` → `200` with `status=UP` and `version`
- [ ] `GET /api/v1/health/ready` → `200` with `database=UP` when Postgres is up
- [ ] Compose marks `api` healthy via readiness probe
- [ ] Volumes persist across `docker compose down` / `up` (notes and covers remain)
- [ ] Postgres/Ollama are not world-exposed by default (API-only publish)

## Core product (Phases 1–5)

- [ ] Register a new user; create a book / chapter / section / note
- [ ] TipTap note edit + save; draft/saved; original body preserved
- [ ] Cover upload/remove; chapter/section reorder
- [ ] Share book as Editor and Viewer; ACL enforced
- [ ] AI offline path: notes usable; Settings/chip show offline + setup hint
- [ ] With Ollama: refine → compare → edit → accept/reject/regenerate; stale handling
- [ ] Search returns ACL-scoped hits only
- [ ] Markdown + JSON export; invalid import rejected; valid import creates new owned book

## Settings / release

- [ ] Settings page shows account, health, AI status, and backup link
- [ ] README + `SETUP.md` match the one-command and non-Docker paths
- [ ] `.env.example` documents install knobs
- [ ] No required cloud accounts for install or AI

## Automated gates

```bash
./mvnw test
cd frontend && npm run build
./scripts/smoke-check.sh
```

All three should pass before tagging a release.
