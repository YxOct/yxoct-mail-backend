# YxOct Mail Backend

Spring Boot backend for YxOct Mail. It manages users, invitations, Stalwart mail accounts, aliases, authentication, administration, and mailbox operations through JMAP.

## Requirements

- Java 21
- Docker Desktop
- MySQL 8.4
- Redis 7.4
- A reachable Stalwart instance for manual end-to-end mail testing

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

Start the local MySQL and Redis dependencies:

```powershell
docker compose up -d mysql redis
docker compose ps mysql redis
```

Both services should become `healthy`. Verify them directly if needed:

```powershell
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -h 127.0.0.1 -uroot --silent'
docker compose exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'
```

The commands should report `mysqld is alive` and `PONG`. Flyway creates or updates the application schema when the backend starts.

Start the backend:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Alternatively, open `MailBackendApplication.java` in VS Code and use `Run Java`.

Do not run `docker compose down -v`; the named volumes contain persistent MySQL, Redis, and Prometheus data.

### Stalwart end-to-end testing

Unit tests and MySQL integration tests do not require an external Stalwart server; the Management API and JMAP clients are mocked. Running the backend with provisioning disabled also does not require a local Stalwart container.

Manual end-to-end testing of registration provisioning, aliases, account settings, reconciliation, mailboxes, and messages requires a deployed Stalwart instance reachable through `STALWART_BASE_URL`. Configure a dedicated test domain and automation account, set `STALWART_MANAGEMENT_API_KEY` and `STALWART_CREDENTIAL_ENCRYPTION_KEY`, then enable `STALWART_PROVISIONING_ENABLED=true`.

The Stalwart instance may run in a local container or on a separate test server. Prefer a non-production instance so test registration, alias, disable, and reconciliation operations cannot alter production mail accounts.

## API and operations

With the `dev` profile running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus` (`ADMIN` only)

API responses use `{ "code", "message", "data" }`. Use OpenAPI for the complete endpoint and schema list.

Operational documentation:

- [Production deployment](deploy/README.md)
- [First administrator bootstrap](deploy/BOOTSTRAP_ADMIN.md)
- [MySQL backup and restore](deploy/mysql/README.md)
- [Stalwart deployment and backup](deploy/stalwart/README.md)
- [Off-host backup retention](deploy/remote-backup/README.md)
- [Monitoring and alerts](monitoring/README.md)

## Testing

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
```

`verify` includes Testcontainers MySQL integration tests, so Docker must be running. Flyway migrations are immutable after application; create a new version instead of editing an applied migration.
