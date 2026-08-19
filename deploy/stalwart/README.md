# Stalwart deployment and backup

This directory manages the non-secret Stalwart container definition and its consistent offline backup task.

## Managed files

- `deploy/stalwart/compose.yaml`: Stalwart container definition.
- `deploy/stalwart/backup/`: local archive creation and rsync upload.
- `deploy/stalwart/systemd/`: daily Stalwart backup units.

Runtime data, Stalwart configuration, certificates, SSH private keys, and real environment files are not stored in Git.

## Deploy Stalwart

The production server keeps its runtime files under `/home/ubuntu/stalwart`:

```text
/home/ubuntu/stalwart/
├── compose.yaml
├── etc/
└── data/
/opt/stalwart-certs/
```

Install the tracked Compose definition without replacing `etc/` or `data/`:

```bash
sudo install -o ubuntu -g ubuntu -m 0644 deploy/stalwart/compose.yaml /home/ubuntu/stalwart/compose.yaml
cd /home/ubuntu/stalwart
docker compose config --quiet
docker compose up -d
docker compose ps
```

SMTP port 25 is public. The management port is bound to server loopback. Add submission, SMTPS, IMAP, or HTTPS ports only after configuring the matching Stalwart listeners and firewall rules.

## Deploy the Stalwart backup task

The source server requires Docker with the Compose plugin, `tar`, `rsync`, `ssh`, `flock`, and `sha256sum`.

```bash
sudo install -o root -g root -m 0750 deploy/stalwart/backup/backup-stalwart.sh /usr/local/sbin/backup-stalwart.sh
sudo install -o root -g root -m 0644 deploy/stalwart/systemd/stalwart-backup.service /etc/systemd/system/
sudo install -o root -g root -m 0644 deploy/stalwart/systemd/stalwart-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now stalwart-backup.timer
```

Create `/etc/default/stalwart-backup` from `deploy/stalwart/backup/stalwart-backup.env.example` only when it does not already exist, then insert the real backup host. `REMOTE_PATH` is relative to the forced `rrsync` root and normally remains `.`.

Test the task manually:

```bash
sudo systemctl start stalwart-backup.service
sudo systemctl status stalwart-backup.service --no-pager -l
sudo journalctl -u stalwart-backup.service -n 100 --no-pager
sudo docker inspect stalwart --format '{{.State.Status}}'
```

The backup script stops Stalwart, creates and validates a temporary archive, atomically publishes the archive, generates a SHA-256 file, uploads both files, and then applies local retention. Its exit trap attempts to restart Stalwart after either success or failure.

## Restore rehearsal

Perform restore checks in an isolated temporary directory, never over the production directory:

```bash
archive=stalwart-YYYY-MM-DD_HH-MM-SS.tar.gz
sha256sum -c "$archive.sha256"
restore_dir="$(mktemp -d /tmp/stalwart-restore-test.XXXXXX)"
tar -xzf "$archive" -C "$restore_dir"
test -f "$restore_dir/compose.yaml"
test -d "$restore_dir/etc"
test -d "$restore_dir/data"
```

A successful backup job proves that an archive was created, not that the application can be recovered. Periodically start a test instance with isolated storage and ports to confirm that accounts and mail data remain readable.

## Routine checks

```bash
systemctl list-timers 'stalwart-backup*'
journalctl -u stalwart-backup.service --since yesterday
find /var/backups/stalwart -maxdepth 1 -type f -name 'stalwart-*' -ls
```

Install the separate [off-host retention cleanup task](../remote-backup/README.md) on the backup server.
