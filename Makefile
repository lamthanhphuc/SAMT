# ==============================================
# SAMT MICROSERVICES - MAKEFILE
# ==============================================
# Shortcuts for common operations

.PHONY: help build up down restart logs clean

help: ## Show this help
	@echo "Available commands:"
	@echo "  make build       - Build all services"
	@echo "  make up          - Start all containers"
	@echo "  make down        - Stop all containers"
	@echo "  make restart     - Restart all containers"
	@echo "  make logs        - View logs"
	@echo "  make clean       - Clean Maven & Docker artifacts"
	@echo "  make db-only     - Start only databases (for IDE dev)"

build: ## Build all Maven projects
	./mvnw clean package -DskipTests

up: ## Start all containers
	docker-compose up -d

down: ## Stop all containers
	docker-compose down

restart: down up ## Restart all containers

logs: ## Follow logs
	docker-compose logs -f

clean: ## Clean Maven target & Docker volumes
	./mvnw clean
	docker-compose down -v

db-only: ## Start only databases (Redis + PostgreSQL)
	docker-compose up -d postgres-identity postgres-core redis
