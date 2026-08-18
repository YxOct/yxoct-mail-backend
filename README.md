# YxOct Mail Backend

Backend service for YxOct Mail.

## Environment

Create `.env` based on `.env.example` and configure the required environment variables before running the application.

The `dev` profile loads `.env` from the project root and requires:

- `DB_URL`: MySQL JDBC URL.
- `DB_USERNAME`: application database user.
- `DB_PASSWORD`: application database password.
- `STALWART_BASE_URL`: Stalwart server base URL.
- `STALWART_TEST_USERNAME`: Development mailbox username.
- `STALWART_TEST_PASSWORD`: Development mailbox password.

For local development, start MySQL and wait for it to become healthy before starting the application:

```powershell
docker compose up -d
docker compose ps
```

`DB_ROOT_PASSWORD` and `DB_NAME` are used by Docker Compose when initializing MySQL. The optional database pool settings in `.env.example` use milliseconds for timeouts.

Flyway applies versioned migrations from `src/main/resources/db/migration` when the application starts. Migration `V1` creates the records required to restore a deleted email to all of its original mailboxes. Applied migrations are tracked in `flyway_schema_history`; never edit a migration after it has been applied. Add a new version instead.

The optional `STALWART_CONNECT_TIMEOUT`, `STALWART_READ_TIMEOUT`, and `STALWART_SESSION_CACHE_TTL` values use Spring Boot duration syntax and default to `5s`, `10s`, and `1m`.

The `prod` profile does not load `.env`. Supply the database variables together with `STALWART_BASE_URL`, `STALWART_USERNAME`, and `STALWART_PASSWORD` through the deployment environment. The application fails during startup when any required setting is missing.

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

## API

- `GET /api/mail/mailboxes`: list mailboxes.
- `GET /api/mail/mailboxes/{mailboxId}/emails?page=1&size=20&keyword=invoice&read=false&starred=true&sortBy=receivedAt&direction=desc`: list emails with optional search, status filters, and sorting. `sortBy` supports `receivedAt`, `sentAt`, `subject`, `from`, `to`, and `size`; `direction` supports `asc` and `desc`. The default is newest first. `keyword` is limited to 200 characters and `size` must be between 1 and 100.
- `GET /api/mail/emails/{id}`: get an email detail, including received attachment metadata.
- `GET /api/mail/emails/{emailId}/attachments/{blobId}`: securely stream an attachment that belongs to the email.
- `PATCH /api/mail/emails/read-status`: update up to 100 emails with `{ "ids": ["email-1"], "read": true }`.
- `PATCH /api/mail/emails/star-status`: update up to 100 emails with `{ "ids": ["email-1"], "starred": true }`.
- `POST /api/mail/emails/move`: move up to 100 emails with `{ "ids": ["email-1"], "targetMailboxId": "archive" }`.
- `POST /api/mail/emails/trash`: move up to 100 emails to Trash with `{ "ids": ["email-1"] }`.
- `POST /api/mail/emails/restore`: restore up to 100 emails with `{ "ids": ["email-1"] }`.
- `DELETE /api/mail/emails`: permanently delete up to 100 emails whose only mailbox is Trash with `{ "ids": ["email-1"] }`.
- `GET /actuator/health`: check application and Stalwart availability.
- `GET /actuator/health/liveness`: check only whether the application is alive.
- `GET /actuator/health/readiness`: check whether the application, database, and Stalwart are ready to serve traffic.

All email update endpoints accept between 1 and 100 IDs. Use a single-element `ids` array for a single-email operation.
Use the dedicated Trash endpoint when the target mailbox has the `trash` role so the original mailbox locations can be saved for restoration.

The `dev` profile also exposes `/actuator/metrics/stalwart.client.requests`, which reports JMAP operation counts, durations, and classified outcomes. Production continues to expose only health information unless a metrics exporter is configured.

Every HTTP response includes an `X-Request-Id`. A valid incoming value is preserved; otherwise the application generates one. The same value is included in logs and forwarded to Stalwart for request correlation. Health details are not exposed over HTTP.

Email summaries and details expose `read` and `starred` boolean fields derived from the JMAP `$seen` and `$flagged` keywords.
Email details expose attachment metadata from Stalwart; attachment binary data is not stored in MySQL.
Email details expose separate `textBody` and sanitized `htmlBody` fields. The legacy `body` field prefers plain text and falls back to sanitized HTML.

Batch status updates return both `updatedIds` and `failed` items because JMAP may apply only part of a request. Duplicate IDs are rejected.

All mail endpoints return the common response shape `{ "code", "message", "data" }`. Important error codes are:

- `1000`: invalid request (`400`).
- `2000`: email not found (`404`).
- `2001`: email restore record not found (`404`).
- `2002`: mailbox not found (`404`).
- `2003`: email is not exclusively in Trash (`409`).
- `2004`: Stalwart connection or protocol failure (`502`).
- `2005`: Stalwart timeout (`504`).
- `2006`: Stalwart authentication failure (`502`).
- `2007`: attachment not found on the email (`404`).

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
