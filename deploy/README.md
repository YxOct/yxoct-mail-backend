# Production deployment

`compose.prod.yaml` runs the backend, MySQL, Redis, and Prometheus. Only the backend is bound to server loopback at `127.0.0.1:8081`; port 8080 remains available for the co-located Stalwart management service, and the server's existing Nginx handles public traffic.

## Prerequisites

- A Linux server with Docker Engine and the Docker Compose plugin.
- An existing Nginx installation with TLS configured for `webmail.yxoct.com`.
- Network access from the backend to the Stalwart management and JMAP endpoints.
- The repository checked out on the server.

MySQL, Redis, and Prometheus remain inside the Compose network and do not publish host ports.

## Production configuration

Keep the real `deploy/.env.prod` only on the server. Create it from `deploy/.env.prod.example` and fill every required value. At minimum, use independent random values for the database, Redis, JWT, Stalwart credential encryption, and Prometheus scrape credentials.

`JWT_SECRET` and `STALWART_CREDENTIAL_ENCRYPTION_KEY` must remain stable. Both use unpadded Base64URL encodings of 32 random bytes. Changing them invalidates sessions or makes stored Stalwart credentials unreadable.

Validate the rendered Compose configuration before changing containers:

```bash
docker compose --env-file deploy/.env.prod -f compose.prod.yaml config --quiet
```

## Initial deployment

The production Compose file pulls the backend image from GHCR. Set `BACKEND_IMAGE_REPOSITORY` and `BACKEND_IMAGE_TAG` in `deploy/.env.prod`, authenticate Docker to GHCR when the package is private, and then start the stack:

```bash
docker compose --env-file deploy/.env.prod -f compose.prod.yaml pull
docker compose --env-file deploy/.env.prod -f compose.prod.yaml up -d
docker compose --env-file deploy/.env.prod -f compose.prod.yaml ps
```

Do not run `docker compose down -v`; the named volumes contain MySQL, Redis, and Prometheus data.

## Continuous deployment

After CI succeeds for a push to `main`, `.github/workflows/cd.yml` builds an ARM64 image, publishes both the verified commit SHA and `latest` tags to GHCR, and deploys the immutable SHA tag to the production server. The deployment script updates only the backend container, waits for readiness, and restores the previous image if readiness fails.

Create a protected GitHub environment named `production` and configure these repository or environment secrets:

- `DEPLOY_HOST`: production server address.
- `DEPLOY_PORT`: SSH port, normally `22`.
- `DEPLOY_USER`: dedicated deployment user, normally `ubuntu`.
- `DEPLOY_SSH_KEY`: private key for that user.
- `DEPLOY_KNOWN_HOSTS`: verified `known_hosts` entry for the production server.

The deployment user must be able to run Docker without `sudo`, read `/opt/yxoct-mail-backend`, update its Git checkout, and read `deploy/.env.prod`. Application secrets remain only in the server environment file and are not copied into GitHub Secrets.

For private GHCR packages, the workflow logs the server into GHCR with the short-lived workflow token before pulling the image. Keep the package linked to this repository so `GITHUB_TOKEN` receives package write and read access.

The server checkout is deployment-managed: do not edit tracked files under `/opt/yxoct-mail-backend` directly. The ignored `deploy/.env.prod` file is preserved across deployments.

## Verify

All services should be running or healthy:

```bash
docker compose --env-file deploy/.env.prod -f compose.prod.yaml ps
curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health/readiness
docker compose --env-file deploy/.env.prod -f compose.prod.yaml logs --tail=100 backend
```

The readiness response should report `UP`. Investigate failed Flyway migrations, database or Redis connectivity, and Stalwart health before reloading Nginx.

## Nginx

Add the locations from `deploy/nginx/webmail-api.conf` to the existing `webmail.yxoct.com` server block. Validate before reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

The snippet preserves `/api/*`, forwards the real client address and protocol, and blocks public `/actuator/*` access.

After reload, verify that the public API is reachable and that actuator endpoints remain unavailable from the internet.

## Operational setup

- [Bootstrap the first administrator](BOOTSTRAP_ADMIN.md)
- [Install MySQL backup and restore](mysql/README.md)
- [Manage Stalwart deployment and backup](stalwart/README.md)
- [Manage off-host backup retention](remote-backup/README.md)
- [Configure monitoring and alerts](../monitoring/README.md)

Runtime data, database credentials, API keys, certificates, SSH keys, and real environment files remain outside Git.
