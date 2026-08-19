# First administrator bootstrap

The administrator API cannot bootstrap an empty database. Run this procedure once on the production server to create an invitation, register the intended owner, and promote that user.

## Create an invitation

From the repository root:

```bash
invitation="yxi$(openssl rand -base64 16 | tr '+/' '-_' | tr -d '=')"
token_hash="$(printf '%s' "$invitation" | sha256sum | awk '{print $1}')"

printf '%s\n' \
  "INSERT INTO registration_invitation (token_hash, status, purpose, expires_at) VALUES ('$token_hash', 'PENDING', 'REGISTRATION', DATE_ADD(NOW(6), INTERVAL 7 DAY));" |
  docker compose --env-file deploy/.env.prod -f compose.prod.yaml exec -T mysql sh -ec '
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    exec mysql --user=root "$MYSQL_DATABASE"
  '

printf '%s\n' "$invitation"
unset invitation token_hash
```

Register `owner@yxoct.com` with the displayed invitation. Do not log or store the invitation after use.

## Promote the registered user

```bash
address='owner@yxoct.com'

printf '%s\n' \
  "UPDATE app_user u JOIN user_mail_account uma ON uma.user_id = u.id JOIN email_address ea ON ea.mail_account_id = uma.mail_account_id AND ea.address_type = 'PRIMARY' SET u.role = 'ADMIN' WHERE ea.normalized_address = '$address';" |
  docker compose --env-file deploy/.env.prod -f compose.prod.yaml exec -T mysql sh -ec '
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    exec mysql --user=root "$MYSQL_DATABASE"
  '
```

Log in again after promotion so the new access token contains the `ADMIN` role. Create all later invitations through the administrator API instead of repeating this database procedure.
