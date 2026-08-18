# YxOct Mail Backend

Backend service for YxOct Mail.

## Environment

Create `.env` based on `.env.example` and configure the required environment variables before running the application.

The `dev` profile loads `.env` from the project root and requires:

- `STALWART_BASE_URL`: Stalwart server base URL.
- `STALWART_TEST_USERNAME`: Development mailbox username.
- `STALWART_TEST_PASSWORD`: Development mailbox password.

The optional `STALWART_CONNECT_TIMEOUT`, `STALWART_READ_TIMEOUT`, and `STALWART_SESSION_CACHE_TTL` values use Spring Boot duration syntax and default to `5s`, `10s`, and `1m`.

The `prod` profile does not load `.env`. Supply `STALWART_BASE_URL`, `STALWART_USERNAME`, and `STALWART_PASSWORD` through the deployment environment. The application fails during startup when any required Stalwart setting is missing.

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
- `GET /api/mail/mailboxes/{mailboxId}/emails?page=1&size=20`: list emails, ordered by newest first. `size` must be between 1 and 100.
- `GET /api/mail/emails/{id}`: get an email detail.
- `GET /actuator/health`: check application and Stalwart availability. Health details are not exposed over HTTP.

All mail endpoints return the common response shape `{ "code", "message", "data" }`. Important error codes are:

- `1000`: invalid request (`400`).
- `2000`: email not found (`404`).
- `2004`: Stalwart connection or protocol failure (`502`).
- `2005`: Stalwart timeout (`504`).
- `2006`: Stalwart authentication failure (`502`).

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
