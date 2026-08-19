# MySQL backup and restore

The production MySQL backup uses `mysqldump --single-transaction`, so the backend and database remain online during normal backups. Backups are compressed, validated, atomically renamed, retained locally, and copied to the off-host backup server through a restricted `rrsync` key.

Production should use both copies: keep short local retention for fast recovery and longer off-host retention for host or disk failure. The supplied defaults are 7 local days and 30 remote days.

## Install on the application server

```bash
sudo install -d -m 700 /etc/yxoct-mail
sudo install -d -m 700 /var/backups/yxoct-mail/mysql
sudo install -m 700 deploy/backup/backup-yxoct-mail-mysql.sh /usr/local/sbin/
sudo install -m 700 deploy/backup/restore-yxoct-mail-mysql.sh /usr/local/sbin/
sudo install -m 644 deploy/systemd/yxoct-mail-mysql-backup.service /etc/systemd/system/
sudo install -m 644 deploy/systemd/yxoct-mail-mysql-backup.timer /etc/systemd/system/
```

If `/etc/yxoct-mail/mysql-backup.conf` does not exist, create it from `deploy/backup/mysql-backup.conf.example`; deployment must never overwrite an existing real configuration. Fill in the project path, Compose environment file, remote host, restricted SSH key, and retention. Keep `REMOTE_ENABLED=true` in production. The database password remains only in the server's production environment file and is never passed on the command line.

Create the remote `mysql` subdirectory inside the forced `rrsync` root, then test one manual backup:

```bash
sudo /usr/local/sbin/backup-yxoct-mail-mysql.sh
sudo find /var/backups/yxoct-mail/mysql -maxdepth 1 -type f -printf '%TY-%Tm-%Td %TH:%TM %12s %p\n' | sort -r | head
sudo gzip -t /var/backups/yxoct-mail/mysql/yxoct-mail-mysql-*.sql.gz
```

The backup server must have the unified cleanup task from `deploy/remote-backup/` installed. It retains both Stalwart archives and MySQL dumps for 30 days by default.

Enable the daily 05:30 timer only after the manual backup and remote copy succeed:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now yxoct-mail-mysql-backup.timer
systemctl list-timers --all | grep yxoct-mail-mysql
```

## Restore rehearsal

Restore is destructive to the current application database. Use an isolated rehearsal server first. The restore script validates the archive, creates a fresh safety backup, stops backend writes, imports the dump, and restarts the backend only if it was previously running.

```bash
sudo /usr/local/sbin/restore-yxoct-mail-mysql.sh \
  /var/backups/yxoct-mail/mysql/yxoct-mail-mysql-YYYY-MM-DD_HH-MM-SS.sql.gz \
  --confirm-destroy-current-database
```

After restoration, verify Flyway version, user counts, primary email mappings, and backend readiness before accepting the recovery.

For an off-host restore, first copy the selected dump back from the restricted backup storage into `/var/backups/yxoct-mail/mysql`, verify it with `gzip -t`, and then invoke the same restore script. Never restore directly into production merely to test an archive; use an isolated rehearsal environment.
