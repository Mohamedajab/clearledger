.PHONY: up down logs test build ui api

up:
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f api ui

test:
	cd services/ledger-api && ./mvnw test
	cd apps/control-plane && npm ci && npm run lint && npm run build

build:
	docker compose build

ui:
	cd apps/control-plane && npm run dev

api:
	cd services/ledger-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
