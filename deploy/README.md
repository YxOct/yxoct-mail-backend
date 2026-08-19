# Production deployment

`compose.prod.yaml` runs the backend, MySQL, Redis, and Prometheus. Only the backend is bound to server loopback at `127.0.0.1:8080`; the server's existing Nginx handles public traffic.

## Start or update

Keep the real `deploy/.env.prod` only on the server. Create it from `deploy/.env.prod.example`, fill every required secret, then run:

```bash
docker compose --env-file deploy/.env.prod -f compose.prod.yaml config --quiet
docker compose --env-file deploy/.env.prod -f compose.prod.yaml up -d --build
docker compose --env-file deploy/.env.prod -f compose.prod.yaml ps
```

Do not run `docker compose down -v`; the named volumes contain MySQL, Redis, and Prometheus data.

## Nginx

Add the locations from `deploy/nginx/webmail-api.conf` to the existing `webmail.yxoct.com` server block. Validate before reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

The snippet preserves `/api/*`, forwards the real client address and protocol, and blocks public `/actuator/*` access.

## Operational setup

- [Bootstrap the first administrator](BOOTSTRAP_ADMIN.md)
- [Install MySQL backup and restore](backup/README.md)
- [Manage Stalwart and off-host backups](MAIL_INFRASTRUCTURE.md)
- [Configure monitoring and alerts](../monitoring/README.md)

Runtime data, database credentials, API keys, certificates, SSH keys, and real environment files remain outside Git.
