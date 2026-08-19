# YxOct Mail Backend

Spring Boot backend for YxOct Mail. It manages users, invitations, Stalwart mail accounts, aliases, authentication, administration, and mailbox operations through JMAP.

## Requirements

- Java 21
- Docker Desktop
- MySQL 8.4
- Redis 7.4
- Stalwart with a management API key

## Configuration

Copy `.env.example` to `.env` and fill in the required values. The `dev` profile loads this file; production reads variables from its deployment environment.

Important settings include:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`
- `MAIL_DOMAIN`
- `STALWART_BASE_URL`, `STALWART_MANAGEMENT_API_KEY`
- `STALWART_CREDENTIAL_ENCRYPTION_KEY`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`

`JWT_SECRET` and `STALWART_CREDENTIAL_ENCRYPTION_KEY` must each be the stable, unpadded Base64URL encoding of 32 random bytes. Rotating them invalidates existing sessions or encrypted mail credentials. Never commit `.env` or production secrets.

## Local development

Start Redis and verify it:

```powershell
docker compose up -d redis
docker compose exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'
```

Keep the existing MySQL container running if it owns the development database. Do not run `docker compose down -v`, because named volumes contain persistent data.

Start the backend:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Alternatively, open `MailBackendApplication.java` in VS Code and use `Run Java`.

## API and operations

With the `dev` profile running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus` (`ADMIN` only)

API responses use `{ "code", "message", "data" }`. Use OpenAPI for the complete endpoint and schema list.

Operational documentation:

- [Production deployment](deploy/README.md)
- [First administrator bootstrap](deploy/BOOTSTRAP_ADMIN.md)
- [MySQL backup and restore](deploy/backup/README.md)
- [Stalwart and off-host backup](deploy/MAIL_INFRASTRUCTURE.md)
- [Monitoring and alerts](monitoring/README.md)

## Testing

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
```

`verify` includes Testcontainers MySQL integration tests, so Docker must be running. Flyway migrations are immutable after application; create a new version instead of editing an applied migration.
