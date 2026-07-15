.PHONY: setup seed dev dev-backend dev-frontend build run test lint clean

setup: ## Install backend (uv) and frontend (npm) dependencies
	cd backend && uv sync
	cd frontend && npm install

seed: ## (Re)create the database with deterministic demo data
	cd backend && uv run python -m app.seed.seed --reset

dev-backend: ## FastAPI with auto-reload on :8000
	cd backend && uv run uvicorn app.main:app --reload --port 8000

dev-frontend: ## Vite dev server on :5173 (proxies /api -> :8000)
	cd frontend && npm run dev

dev: ## Run backend + frontend together
	cd frontend && npx concurrently -k -n api,web -c blue,green \
		"cd ../backend && uv run uvicorn app.main:app --reload --port 8000" \
		"npm run dev"

build: ## Production frontend build (served by FastAPI in `make run`)
	cd frontend && npm run build

run: ## Single-process demo: API + built frontend on :8000
	cd backend && uv run uvicorn app.main:app --port 8000

test: ## Backend test suite
	cd backend && uv run pytest -q

lint:
	cd frontend && npm run lint

clean:
	rm -rf backend/data/*.db frontend/dist
