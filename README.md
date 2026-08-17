# YxOct Mail Backend

Backend service for YxOct Mail.

## Environment

Create `.env` based on `.env.example` and configure the required environment variables before running the application.

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
