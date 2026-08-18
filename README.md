# YxOct Mail Backend

Backend service for YxOct Mail.

## Environment

Create `.env` based on `.env.example` and configure the required environment variables before running the application.

The `dev` profile loads `.env` from the project root and requires:

- `DB_URL`: MySQL JDBC URL.
- `DB_USERNAME`: application database user.
- `DB_PASSWORD`: application database password.
- `MAIL_DOMAIN`: domain appended to the local part selected during registration.
- `STALWART_BASE_URL`: Stalwart server base URL.
- `STALWART_TEST_USERNAME`: Development mailbox username.
- `STALWART_TEST_PASSWORD`: Development mailbox password.

When account provisioning is enabled, also set:

- `STALWART_MANAGEMENT_API_KEY`: restricted API key used only to query domains and provision accounts.
- `STALWART_CREDENTIAL_ENCRYPTION_KEY`: Base64-encoded 256-bit key used to encrypt internal mailbox credentials.

For local development, start MySQL and wait for it to become healthy before starting the application:

```powershell
docker compose up -d
docker compose ps
```

`DB_ROOT_PASSWORD` and `DB_NAME` are used by Docker Compose when initializing MySQL. The optional database pool settings in `.env.example` use milliseconds for timeouts.

Flyway applies versioned migrations from `src/main/resources/db/migration` when the application starts. The migrations cover deleted-email restoration, users and mail accounts, and registration invitations. Applied migrations are tracked in `flyway_schema_history`; never edit a migration after it has been applied. Add a new version instead.

`APP_TIME_ZONE` defaults to `Asia/Shanghai` and should match the MySQL session time zone. The optional timeout, cache, invitation, and provisioning interval values use Spring Boot duration syntax. Account provisioning is disabled by default in development. Enable it only after setting both provisioning secrets. Generate the credential encryption key with `openssl rand -base64 32` or an equivalent cryptographically secure generator, and keep it stable; changing or losing it makes existing internal mailbox credentials unreadable.

The `prod` profile does not load `.env`. Supply the database variables together with `STALWART_BASE_URL`, `STALWART_USERNAME`, `STALWART_PASSWORD`, `STALWART_MANAGEMENT_API_KEY`, and `STALWART_CREDENTIAL_ENCRYPTION_KEY` through the deployment environment. Provisioning is enabled by default in production, and the application fails during startup when a provisioning secret is missing or invalid.

## Run

Select a profile explicitly when starting the application.

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

Invitation-based registration creates the local user, primary email address, ownership relation, and a mail account in `PROVISIONING` state. A background worker claims pending accounts with a lease, provisions them through Stalwart's management JMAP API, and records `ACTIVE` or `FAILED`; failed work is retried with bounded exponential backoff. Remote accounts carry the local account ID in their description so a retry can safely distinguish its own previous creation from an unrelated address conflict. Internal mailbox credentials are random, encrypted with AES-256-GCM, and never returned by an API. Invitation tokens use the format `yxi` followed by 22 URL-safe Base64 characters (128 bits of randomness), are returned only when created, and only their SHA-256 hashes are stored. Invitations carry a purpose instead of granting persistent account or address quotas. User passwords are stored as versioned Argon2 hashes.

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
