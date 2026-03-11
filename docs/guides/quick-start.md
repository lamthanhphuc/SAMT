# Quick Start

## Prerequisites

- Java 21
- Maven 3.9+
- Docker with Compose v2

## Fastest Local Path

```bash
./mvnw clean install -DskipTests
docker compose up -d --build
curl http://localhost:9080/actuator/health
```

## What To Read Next

- [local-development.md](local-development.md) for Docker and IDE workflows.
- [../architecture/service-topology.md](../architecture/service-topology.md) for system structure.
- [../services/README.md](../services/README.md) for service-specific references.