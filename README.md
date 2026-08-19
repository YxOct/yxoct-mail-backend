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
docker compose exec -T redis redis-cli ping
```

The last command should return `PONG`. Keep an existing MySQL container running if it already owns the project's database. Do not run `docker compose down -v`, because that removes database and Redis volumes.

Start the application:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

In VS Code, open `MailBackendApplication.java` and use the editor's `Run Java` action. The workspace configuration uses the `dev` profile and writes output to the integrated terminal.

For production, set `SPRING_PROFILES_ACTIVE=prod` and provide all secrets through the deployment environment. Do not commit `.env` or secret values.

## API documentation

With the `dev` profile running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

All responses use `{ "code", "message", "data" }`. Use the generated OpenAPI document for the complete endpoint and schema list.

Important flows:

- `POST /api/auth/register`: register with a single-use invitation.
- `POST /api/auth/login`: obtain access and refresh tokens.
- `POST /api/auth/refresh`: rotate the refresh token.
- `POST /api/auth/logout`: revoke one refresh token.
- `GET /api/auth/me`: inspect the authenticated user and mail-account status.
- `GET /api/mail/mailboxes`: list mailboxes for the current user.
- `POST /api/admin/invitations`: create a registration or email-address invitation (`ADMIN` only).
- `GET /api/admin/users`: list users (`ADMIN` only).
- `POST /api/admin/users/{userId}/disable`: disable a user and owned mail accounts.
- `POST /api/admin/users/{userId}/enable`: re-enable a disabled user.
- `POST /api/admin/users/{userId}/logout`: revoke all sessions without changing account status.

Access tokens contain the user's authentication version. Disabling, enabling, or forcing logout increments that version, revokes refresh tokens, and immediately invalidates previously issued access tokens. Users must log in again after these operations.

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
