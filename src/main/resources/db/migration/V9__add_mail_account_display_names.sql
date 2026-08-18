ALTER TABLE mail_account
    ADD COLUMN display_name VARCHAR(100) NULL;

UPDATE mail_account ma
SET display_name = COALESCE(
    (
        SELECT SUBSTRING(ea.normalized_address, 1, LOCATE('@', ea.normalized_address) - 1)
        FROM email_address ea
        WHERE ea.mail_account_id = ma.id
          AND ea.address_type = 'PRIMARY'
        LIMIT 1
    ),
    CONCAT('mail-account-', ma.id)
);

ALTER TABLE mail_account
    MODIFY COLUMN display_name VARCHAR(100) NOT NULL;
