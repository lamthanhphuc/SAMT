# SAMT

SAMT is a Java 21 and Spring Boot microservice platform with repository documentation organized under `docs/`.

The repository includes a production-style backend reliability platform with iterative QA orchestration, contract and regression validation, performance baselines, chaos experiments, security hardening probes, architecture scanning, and dashboard reporting.

## Local OWASP Security Scan (optional)

Use `NVD_API_KEY` only for security scanning, not for runtime services.

1. Copy `.env.example` to `.env` and set `NVD_API_KEY`.
2. Run scan with dedicated compose override:

```bash
docker compose -f docker-compose.yml -f docker-compose.security-scan.yml run --rm owasp-dependency-check
```

## Navigation

- [Documentation index](docs/README.md)
- [Architecture overview](docs/architecture/service-topology.md)
- [Quick start](docs/guides/quick-start.md)
- [API documentation](docs/api/README.md)
- [Automated QA guide](docs/guides/automated-qa.md)
