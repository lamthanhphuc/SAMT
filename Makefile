# ==============================================
# SAMT MICROSERVICES - MAKEFILE
# ==============================================
# Shortcuts for common operations

.PHONY: help build build-skip-tests up down restart logs clean

COMPOSE = docker compose --env-file .env -f config/docker/docker-compose.yml

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
	$(COMPOSE) up -d

down: ## Stop all containers
	$(COMPOSE) down

restart: down up ## Restart all containers

logs: ## Follow logs
	$(COMPOSE) logs -f

clean: ## Clean Maven target & Docker volumes
	./mvnw clean
	$(COMPOSE) down -v

db-only: ## Start only databases (Redis + PostgreSQL)
	$(COMPOSE) up -d postgres-identity postgres-core redis



