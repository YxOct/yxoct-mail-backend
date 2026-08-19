# Off-host backup retention

The backup server stores Stalwart archives at the forced `rrsync` root and MySQL dumps in its `mysql/` subdirectory. The cleanup task applies independent retention periods to both kinds of backup.

## Install

```bash
sudo install -d -o ubuntu -g ubuntu -m 0750 /var/backups/yxoct-mail
sudo install -o root -g root -m 0750 deploy/remote-backup/cleanup-yxoct-mail-backups.sh /usr/local/sbin/cleanup-yxoct-mail-backups.sh
sudo install -o root -g root -m 0644 deploy/remote-backup/systemd/yxoct-mail-backup-cleanup.service /etc/systemd/system/
sudo install -o root -g root -m 0644 deploy/remote-backup/systemd/yxoct-mail-backup-cleanup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now yxoct-mail-backup-cleanup.timer
```

Create `/etc/default/yxoct-mail-backup-cleanup` from `deploy/remote-backup/yxoct-mail-backup-cleanup.env.example` only when it does not already exist. Never overwrite the server's real configuration during deployment.

The supplied defaults retain both Stalwart and MySQL backups for 30 days. Incomplete rsync transfer directories are not matched by the cleanup rules.

## Verify

```bash
sudo systemctl start yxoct-mail-backup-cleanup.service
sudo systemctl status yxoct-mail-backup-cleanup.service --no-pager -l
sudo journalctl -u yxoct-mail-backup-cleanup.service -n 50 --no-pager
systemctl list-timers 'yxoct-mail-backup-cleanup*'
```
