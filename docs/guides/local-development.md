# Local Development

## Build Context Rule

All service Dockerfiles assume the repository root is the Docker build context because they copy the root `pom.xml` and multiple module `pom.xml` files.

Use:

```bash
docker compose up -d --build
```

Do not switch the build context to an individual service directory unless the Dockerfiles are rewritten.

## Local Secrets

Some services require local private keys for development. Create them under `.local-certs` and do not commit them.

```bash
mkdir -p .local-certs
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local-certs/identity-jwt-private.pkcs8.pem
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local-certs/gateway-internal-jwt-private.pkcs8.pem
```

## Common Workflows

### Full stack

```bash
./mvnw clean install -DskipTests
docker compose up -d --build
docker compose ps
```

### Databases and infrastructure only

```bash
docker compose up -d postgres-identity postgres-core redis
```

### Health checks

```bash
curl http://localhost:9080/actuator/health
curl http://localhost:8081/actuator/health
```

## Related Documents

- [../../DEPLOYMENT.md](../../DEPLOYMENT.md)
- [quick-start.md](quick-start.md)
- [security-migration.md](security-migration.md)