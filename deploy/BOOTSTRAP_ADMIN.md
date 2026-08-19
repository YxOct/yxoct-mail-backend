# First administrator bootstrap

The administrator API cannot bootstrap an empty database. A database operator must create the first registration invitation, register the first user, and promote that user once.

## Create an invitation

Run from PowerShell with the production MySQL container available:

```powershell
$bytes = [byte[]]::new(16)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$suffix = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$invitation = "yxi$suffix"
$hash = [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($invitation))
$tokenHash = [Convert]::ToHexString($hash).ToLowerInvariant()
$sql = "INSERT INTO registration_invitation (token_hash, status, purpose, expires_at) VALUES ('$tokenHash', 'PENDING', 'REGISTRATION', DATE_ADD(NOW(6), INTERVAL 7 DAY));"
$sql | docker exec -i <mysql-container> sh -c 'mysql --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
$invitation
```

Register the intended owner with the generated invitation. Do not log or store the invitation after use.

## Promote the registered user

```powershell
$address = "owner@yxoct.com"
$sql = "UPDATE app_user u JOIN user_mail_account uma ON uma.user_id = u.id JOIN email_address ea ON ea.mail_account_id = uma.mail_account_id AND ea.address_type = 'PRIMARY' SET u.role = 'ADMIN' WHERE ea.normalized_address = '$address';"
$sql | docker exec -i <mysql-container> sh -c 'mysql --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

Log in again after promotion so the new access token contains the `ADMIN` role. Create later invitations through the administrator API instead of repeating this database procedure.
