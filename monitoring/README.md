# Monitoring and alerts

The project includes Prometheus scrape configuration and alert rules. Prometheus records alert state but does not send notifications until Alertmanager and a notification receiver are configured.

## Local monitoring

Generate a dedicated scrape token and save it in `.env` as `PROMETHEUS_SCRAPE_TOKEN`:

```powershell
$bytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
```

Restart the backend, then start Prometheus:

```powershell
docker compose --profile monitoring up -d prometheus
```

Verify:

- Targets: `http://localhost:9090/targets`
- Alerts: `http://localhost:9090/alerts`
- Backend readiness: `http://localhost:8080/actuator/health/readiness`

The `yxoct-mail-backend` target should report `UP`. Alert definitions are stored in `monitoring/prometheus/alerts.yml`.

## Production

Prometheus is not published by `compose.prod.yaml`, so its UI is not reachable from the host by default. Routine health can be checked inside the container:

```bash
docker compose --env-file deploy/.env.prod -f compose.prod.yaml exec -T prometheus \
  wget -qO- http://127.0.0.1:9090/-/ready
```

If temporary UI access is required, add a Compose override that binds `127.0.0.1:9090:9090`, recreate only Prometheus, and then use an SSH tunnel to the server loopback port. Remove the override when investigation is complete. Never publish Prometheus or unrestricted actuator endpoints on a public interface.

Add Alertmanager only after choosing a delivery channel such as email, webhook, or another incident system. Keep receiver credentials in the server environment, not in Git.
