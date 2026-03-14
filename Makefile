# ==============================================
# SAMT MICROSERVICES - MAKEFILE
# ==============================================
# Shortcuts for common operations

.PHONY: help build build-skip-tests up down restart logs clean

help: ## Show this help
	@echo "Available commands:"
	@echo "  make build       - Build all services"
	@echo "  make build-skip-tests - Build all services (skip tests)"
	@echo "  make up          - Start all containers"
	@echo "  make down        - Stop all containers"
	@echo "  make restart     - Restart all containers"
	@echo "  make logs        - View logs"
	@echo "  make clean       - Clean Maven & Docker artifacts"
	@echo "  make db-only     - Start only databases (for IDE dev)"

build: ## Build all Maven projects (includes tests)
	./mvnw -B clean verify

build-skip-tests: ## Build all Maven projects (skip tests)
	./mvnw -B clean package -DskipTests

up: ## Start all containers
	docker compose -f config/docker/docker-compose.yml up -d

down: ## Stop all containers
	docker compose -f config/docker/docker-compose.yml down

restart: down up ## Restart all containers

logs: ## Follow logs
	docker compose -f config/docker/docker-compose.yml logs -f

clean: ## Clean Maven target & Docker volumes
	./mvnw clean
	docker compose -f config/docker/docker-compose.yml down -v

db-only: ## Start only databases (Redis + PostgreSQL)
	docker compose -f config/docker/docker-compose.yml up -d postgres-identity postgres-core redis



