# YxOct Mail Backend

Backend service for YxOct Mail.

## Environment

Create `.env` based on `.env.example` and configure the required environment variables before running the application.

The default `dev` profile loads `.env` from the project root and requires:

- `STALWART_BASE_URL`: Stalwart server base URL.
- `STALWART_TEST_USERNAME`: Development mailbox username.
- `STALWART_TEST_PASSWORD`: Development mailbox password.

The optional `STALWART_CONNECT_TIMEOUT` and `STALWART_READ_TIMEOUT` values use Spring Boot duration syntax and default to `5s` and `10s`.

The `prod` profile does not load `.env`. Supply `STALWART_BASE_URL`, `STALWART_USERNAME`, and `STALWART_PASSWORD` through the deployment environment. The application fails during startup when any required Stalwart setting is missing.

## Run

**Windows:**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**

```bash
./mvnw spring-boot:run
```

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
