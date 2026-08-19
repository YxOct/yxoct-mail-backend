# YxOct Mail Backend

Spring Boot backend for YxOct Mail. It manages application users, registration invitations, Stalwart mail accounts, aliases, and mailbox operations through JMAP.

## Requirements

- Java 21
- Docker Desktop
- MySQL 8.4
- Redis 7.4
- A Stalwart server with a management API key

## Configuration

Copy `.env.example` to `.env` and fill in the required values. The `dev` profile loads this file through `application-dev.yml`; the `prod` profile reads variables from the deployment environment.

Required settings:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`: stable, unpadded Base64URL encoding of 32 random bytes
- `MAIL_DOMAIN`
- `STALWART_BASE_URL`, `STALWART_MANAGEMENT_API_KEY`
- `STALWART_CREDENTIAL_ENCRYPTION_KEY`: stable, unpadded Base64URL encoding of 32 random bytes
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`

Use a dedicated Stalwart automation account. Its role needs the domain/account query, get, create, and update permissions required by provisioning and alias/display-name synchronization. Do not grant account-destroy permissions for normal operation. After validating the key, the automation account's password may be removed.

Keep `JWT_SECRET` and `STALWART_CREDENTIAL_ENCRYPTION_KEY` stable. Rotating either one invalidates existing data. Redis is required for protected requests; requests fail closed when Redis is unavailable.

## Local development

Start Redis:

```powershell
docker compose up -d redis
docker compose ps
docker compose exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'
```

The last command should return `PONG`. Compose refuses to start Redis when `REDIS_PASSWORD` is empty or missing. Keep an existing MySQL container running if it already owns the project's database. Do not run `docker compose down -v`, because that removes database and Redis volumes.

Start the application:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

In VS Code, open `MailBackendApplication.java` and use the editor's `Run Java` action. The workspace configuration uses the `dev` profile and writes output to the integrated terminal.

For production, set `SPRING_PROFILES_ACTIVE=prod` and provide all secrets through the deployment environment. Do not commit `.env` or secret values.

Build the backend container image:

```powershell
docker build --tag yxoct-mail-backend:local .
```

The image runs as a non-root user and checks `/actuator/health/readiness`. Configure it through environment variables; do not copy `.env` into the image. `JAVA_TOOL_OPTIONS` can be used for JVM memory and runtime options.

## Production deployment

`compose.prod.yaml` runs the backend, MySQL, Redis, and Prometheus. MySQL, Redis, and Prometheus do not publish ports. The backend is bound only to server loopback at `127.0.0.1:8080`, so the server's existing Nginx can reach it without exposing it to the internet.

On the server, copy `deploy/.env.prod.example` to a file outside Git and fill every secret. Then validate and start the stack:

```bash
docker compose --env-file deploy/.env.prod -f compose.prod.yaml config --quiet
docker compose --env-file deploy/.env.prod -f compose.prod.yaml up -d --build
docker compose --env-file deploy/.env.prod -f compose.prod.yaml ps
```

Add the locations from `deploy/nginx/webmail-api.conf` to the existing `webmail.yxoct.com` Nginx `server` block. The snippet preserves `/api/*`, forwards the real client IP and protocol, and blocks public `/actuator/*` access. Validate with `nginx -t` before reloading Nginx. Do not run `down -v`; the named volumes contain database, Redis, and Prometheus data.

## API documentation

With the `dev` profile running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus` (`ADMIN` only)

All responses use `{ "code", "message", "data" }`. Use the generated OpenAPI document for the complete endpoint and schema list.

## Local monitoring

Generate a dedicated scrape token and add it to `.env` as `PROMETHEUS_SCRAPE_TOKEN`:

```powershell
$bytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
```

Restart the backend after adding the token, then start Prometheus:

```powershell
docker compose --profile monitoring up -d prometheus
```

Open `http://localhost:9090/targets` to verify that `yxoct-mail-backend` is `UP`, and open `http://localhost:9090/alerts` to inspect alert state. Prometheus stores alert state locally but does not send notifications; Alertmanager can be added later when a notification channel is selected.

Important flows:

- `POST /api/auth/register`: register with a single-use invitation.
- `POST /api/auth/login`: obtain access and refresh tokens.
- `POST /api/auth/refresh`: rotate the refresh token.
- `POST /api/auth/logout`: revoke one refresh token.
- `POST /api/auth/password`: change the current user's password and revoke existing sessions.
- `GET /api/auth/me`: inspect the authenticated user and mail-account status.
- `GET /api/mail/mailboxes`: list mailboxes for the current user.
- `POST /api/admin/invitations`: create a registration or email-address invitation (`ADMIN` only).
- `GET /api/admin/users`: list users (`ADMIN` only).
- `GET /api/admin/users/{userId}/audits`: inspect a user's administrative audit history.
- `GET /api/admin/mail-accounts/provisioning`: list mail accounts awaiting or failing provisioning.
- `POST /api/admin/mail-accounts/{mailAccountId}/retry-provisioning`: schedule an immediate provisioning retry.
- `GET /api/admin/mail-accounts/drifts`: inspect detected account-state, display-name, and alias drift.
- `POST /api/admin/mail-accounts/{mailAccountId}/repair-drift`: repair a detected account-state difference.
- `POST /api/admin/users/{userId}/disable`: disable a user and owned mail accounts.
- `POST /api/admin/users/{userId}/enable`: re-enable a disabled user.
- `POST /api/admin/users/{userId}/logout`: revoke all sessions without changing account status.
- `POST /api/admin/users/{userId}/password`: issue a one-time temporary password and require the user to change it.

Access tokens contain the user's authentication version. Disabling, enabling, forcing logout, or resetting a password increments that version, revokes refresh tokens, and immediately invalidates previously issued access tokens. A user signed in with an administrator-issued temporary password must change it before using normal APIs.

## First administrator

The administrator API cannot bootstrap an empty database. A database operator must create the first registration invitation, register the first user, then promote that user once:

```powershell
$bytes = [byte[]]::new(16)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$suffix = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$invitation = "yxi$suffix"
$hash = [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($invitation))
$tokenHash = [Convert]::ToHexString($hash).ToLowerInvariant()
$sql = "INSERT INTO registration_invitation (token_hash, status, purpose, expires_at) VALUES ('$tokenHash', 'PENDING', 'REGISTRATION', DATE_ADD(NOW(6), INTERVAL 7 DAY));"
$sql | docker exec -i <mysql-container> sh -c 'mysql --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
$invitation
```

After registration, promote the chosen address:

```powershell
$address = "owner@yxoct.com"
$sql = "UPDATE app_user u JOIN user_mail_account uma ON uma.user_id = u.id JOIN email_address ea ON ea.mail_account_id = uma.mail_account_id AND ea.address_type = 'PRIMARY' SET u.role = 'ADMIN' WHERE ea.normalized_address = '$address';"
$sql | docker exec -i <mysql-container> sh -c 'mysql --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

Log in again after promotion so the new JWT contains the `ADMIN` role.

## Testing

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

`verify` includes Testcontainers MySQL integration tests, so Docker must be running. Flyway applies migrations automatically at startup; never edit an applied migration—add a new version instead.

Formatting:

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd spotless:apply
```
