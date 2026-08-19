# YxOct Mail Backend

Backend service for YxOct Mail.

## Environment

Create `.env` based on `.env.example` and configure the required environment variables before running the application.

The `dev` profile loads `.env` from the project root and requires:

- `DB_URL`: MySQL JDBC URL.
- `DB_USERNAME`: application database user.
- `DB_PASSWORD`: application database password.
- `JWT_SECRET`: unpadded Base64URL-encoded 256-bit key used to sign access tokens.
- `MAIL_DOMAIN`: domain appended to the local part selected during registration.
- `STALWART_BASE_URL`: Stalwart server base URL.
- `STALWART_MANAGEMENT_API_KEY`: API key used for readiness checks and account management.

The management API key should belong to a dedicated Stalwart automation account. Assign that account a custom role which extends the built-in `User` role and explicitly enables only `sysDomainQuery`, `sysDomainGet`, `sysAccountQuery`, `sysAccountGet`, `sysAccountCreate`, and `sysAccountUpdate`. Create the key with `Same permissions as account`. The `User` role inheritance is required because provisioning grants the built-in `User` role to each new mailbox and Stalwart rejects attempts to grant permissions the caller does not hold. `sysAccountUpdate` is required for synchronizing display names and aliases.

When account provisioning is enabled, also set:

- `STALWART_CREDENTIAL_ENCRYPTION_KEY`: unpadded Base64URL-encoded 256-bit key used to encrypt internal mailbox credentials.

The provisioning account should be used only for automation. After validating its API key, its password credential can be removed. Do not grant account destroy permissions during normal operation. In production, restrict the API key to the backend server's fixed egress IP when possible.

`JWT_SECRET` must also remain stable. Changing it immediately invalidates every access token. Access tokens expire after 15 minutes by default; opaque refresh tokens expire after 30 days, are stored only as SHA-256 hashes, and are rotated whenever they are used.

For local development, start MySQL and wait for it to become healthy before starting the application:

```powershell
docker compose up -d
docker compose ps
```

`DB_ROOT_PASSWORD` and `DB_NAME` are used by Docker Compose when initializing MySQL. The optional database pool settings in `.env.example` use milliseconds for timeouts.

Flyway applies versioned migrations from `src/main/resources/db/migration` when the application starts. The migrations cover deleted-email restoration, users and mail accounts, and registration invitations. Applied migrations are tracked in `flyway_schema_history`; never edit a migration after it has been applied. Add a new version instead.

`APP_TIME_ZONE` defaults to `Asia/Shanghai` and should match the MySQL session time zone. The optional timeout, cache, invitation, and provisioning interval values use Spring Boot duration syntax. Account provisioning is disabled by default in development. Enable it only after setting both provisioning secrets. Generate the credential encryption key from 32 cryptographically random bytes, encode it as Base64URL without padding (43 characters), and keep it stable; changing or losing it makes existing internal mailbox credentials unreadable.

The `prod` profile does not load `.env`. Supply the database variables together with `JWT_SECRET`, `STALWART_BASE_URL`, `STALWART_MANAGEMENT_API_KEY`, and `STALWART_CREDENTIAL_ENCRYPTION_KEY` through the deployment environment. Provisioning is enabled by default in production, and the application fails during startup when a required authentication or provisioning secret is missing or invalid.

## Run

Select a profile explicitly when starting the application.

In VS Code, open `MailBackendApplication.java` and use `Run Java` or `Debug Java` in the editor title bar. The committed workspace configuration selects the `dev` profile, reads `.env` through `application-dev.yml`, writes output to the integrated terminal, and disables DevTools restart so the stop button terminates the application JVM. Restart the application manually after recompiling code changes.

**Windows (development):**

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

**Linux / macOS (development):**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

For production, set `SPRING_PROFILES_ACTIVE=prod` and provide the production Stalwart variables through the deployment environment.

## API Documentation

The `dev` profile exposes interactive OpenAPI documentation after the application starts:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

OpenAPI and Swagger UI are disabled by default, including under the `prod` profile. Enable them explicitly only when production API documentation should be publicly reachable.

## Testing

Run the fast test suite, which uses an in-memory H2 database in MySQL compatibility mode:

```powershell
.\mvnw.cmd test
```

Run the complete verification suite, including the Testcontainers integration tests against a temporary MySQL 8.4 instance:

```powershell
.\mvnw.cmd verify
```

The complete suite requires Docker to be running. The temporary database uses a random host port and does not access the MySQL instance managed by `compose.yaml`.

## API Behavior

Use the generated OpenAPI documentation for the current endpoint list, request parameters, and request bodies.

All email update endpoints accept between 1 and 100 IDs. Use a single-element `ids` array for a single-email operation.
Use the dedicated Trash endpoint when the target mailbox has the `trash` role so the original mailbox locations can be saved for restoration.

The `dev` profile also exposes `/actuator/metrics/stalwart.client.requests`, which reports JMAP operation counts, durations, and classified outcomes. Production continues to expose only health information unless a metrics exporter is configured.

Every HTTP response includes an `X-Request-Id`. A valid incoming value is preserved; otherwise the application generates one. The same value is included in logs and forwarded to Stalwart for request correlation. Health details are not exposed over HTTP.

Email summaries and details expose `read` and `starred` boolean fields derived from the JMAP `$seen` and `$flagged` keywords.
Email details expose attachment metadata from Stalwart; attachment binary data is not stored in MySQL.
Email details expose separate `textBody` and sanitized `htmlBody` fields. The legacy `body` field prefers plain text and falls back to sanitized HTML.
Safe `cid:` image references in HTML are rewritten to the attachment endpoint. Remote and unmatched images are blocked by removing their source URLs.
An attachment is marked as inline only when its MIME disposition is `inline`; a Content-ID alone does not make a regular attachment inline.

Batch status updates return both `updatedIds` and `failed` items because JMAP may apply only part of a request. Duplicate IDs are rejected.

Authentication uses the primary email address and the password chosen during registration. Send the access token as `Authorization: Bearer <token>`. `GET /api/auth/me` returns the authenticated user's identity, role, primary email address, display name, and mail account provisioning status so clients can wait for `ACTIVE` before opening the mailbox. `GET /api/mail/accounts/{mailAccountId}/addresses` lists the primary address followed by aliases for an owned account. `PATCH /api/mail/accounts/{mailAccountId}` updates the display name of an owned active account in both Stalwart and MySQL. `POST /api/mail/accounts/{mailAccountId}/aliases` consumes a single-use `EMAIL_ADDRESS` invitation and adds the submitted local part as an alias of an owned active account in Stalwart and MySQL. Refreshing rotates the refresh token, so clients must replace both stored tokens with the returned pair. Logging out requires only the submitted refresh token and remains available after an access token expires. Every mail request resolves the authenticated user's active owned mail account and decrypts its internal Stalwart credential for that request; there is no configured mailbox credential fallback. Stalwart readiness uses the management API key instead of a mailbox login.

Invitation management under `/api/admin/invitations` requires the `ADMIN` role. Administrators can create single-use `REGISTRATION` or `EMAIL_ADDRESS` invitations, list invitation metadata, and revoke pending invitations. The plaintext token is returned only by the create operation. Creation and revocation actor IDs are retained for audit purposes.

All API endpoints return the common response shape `{ "code", "message", "data" }`. Important top-level HTTP error codes are:

- `1000`: invalid request (`400`).
- `1001`: unexpected server error (`500`).
- `1002`: request resource not found (`404`).
- `2000`: email not found (`404`).
- `2002`: mailbox not found (`404`).
- `2004`: Stalwart connection or protocol failure (`502`).
- `2005`: Stalwart timeout (`504`).
- `2006`: Stalwart authentication failure (`502`).
- `2007`: attachment not found on the email (`404`).
- `3000`: invalid registration invitation (`400`).
- `3001`: expired registration invitation (`410`).
- `3002`: registration invitation already used (`409`).
- `3003`: registration invitation revoked (`410`).
- `3004`: email address unavailable (`409`).
- `4000`: access authentication failed or missing (`401`).
- `4001`: user account disabled (`403`).
- `4002`: insufficient permission (`403`).
- `4003`: refresh token invalid or expired (`401`).
- `4004`: the user's mail account is not active yet (`409`).
- `4005`: login email address or password is incorrect (`401`).

Invitation-based registration creates the local user, primary email address, ownership relation, and a mail account in `PROVISIONING` state. The optional `displayName` is trimmed, validated as plain text, defaults to the submitted email local part, and becomes the Stalwart account's Full Name. A background worker claims pending accounts with a lease, provisions them through Stalwart's management JMAP API, and records `ACTIVE` or `FAILED`; failed work is retried with bounded exponential backoff. If a retry finds an existing remote account, the worker verifies it with the persisted internal credential before adopting it, so the visible Full Name is not used as technical metadata. Internal mailbox credentials are random, encrypted with AES-256-GCM, and never returned by an API. Invitation tokens use the format `yxi` followed by 22 URL-safe Base64 characters (128 bits of randomness), are returned only when created, and only their SHA-256 hashes are stored. Invitations carry a purpose instead of granting persistent account or address quotas. User passwords are stored as versioned Argon2 hashes.

## First Administrator

Administrator invitation APIs intentionally cannot bootstrap an empty installation. A database operator must create the first invitation and promote the first registered user once. Run these commands from the project root while the local MySQL container is running.

Generate a correctly formatted invitation and insert only its hash:

```powershell
$bytes = [byte[]]::new(16)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$suffix = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$invitation = "yxi$suffix"
$hashBytes = [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($invitation))
$tokenHash = [Convert]::ToHexString($hashBytes).ToLowerInvariant()
$insertSql = "INSERT INTO registration_invitation (token_hash, status, purpose, expires_at) VALUES ('$tokenHash', 'PENDING', 'REGISTRATION', DATE_ADD(NOW(6), INTERVAL 7 DAY));"
$insertSql | docker compose exec -T mysql sh -c 'mysql --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
$invitation
```

Use the printed token once with the registration endpoint. After registration succeeds, replace the address below and promote that user:

```powershell
$adminAddress = "owner@yxoct.com"
$promoteSql = "UPDATE app_user u JOIN user_mail_account uma ON uma.user_id = u.id JOIN email_address ea ON ea.mail_account_id = uma.mail_account_id AND ea.address_type = 'PRIMARY' SET u.role = 'ADMIN' WHERE ea.normalized_address = '$adminAddress'; SELECT ROW_COUNT() AS promoted;"
$promoteSql | docker compose exec -T mysql sh -c 'mysql --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

The result must report `promoted = 1`. Log in again after promotion because previously issued JWTs still contain the old `USER` role. From then on, create and revoke invitations through the administrator API. Do not keep a reusable bootstrap secret in application configuration.

A batch operation can partially succeed. In that case the HTTP response remains successful, while `data.failed` contains a result for each failed email. Common per-email codes are `2000` (email not found), `2001` (restore record not found), `2003` (email is not exclusively in Trash), and `2004` (mail service failure).

## Code Style

Java code is formatted with `google-java-format` and checked with Spotless.

**Check formatting:**

```powershell
.\mvnw.cmd spotless:check
```

**Apply formatting automatically:**

```powershell
.\mvnw.cmd spotless:apply
```
