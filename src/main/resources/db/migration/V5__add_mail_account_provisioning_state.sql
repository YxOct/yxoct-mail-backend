ALTER TABLE mail_account
    ADD COLUMN credential_ciphertext VARCHAR(512) NULL;

ALTER TABLE mail_account
    ADD COLUMN provisioning_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE mail_account
    ADD COLUMN provisioning_lease_until TIMESTAMP(6) NULL;

ALTER TABLE mail_account
    ADD COLUMN next_provisioning_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE mail_account
    ADD COLUMN last_provisioning_error VARCHAR(64) NULL;

ALTER TABLE mail_account
    ADD CONSTRAINT chk_mail_account_provisioning_attempts
        CHECK (provisioning_attempts >= 0);

CREATE INDEX idx_mail_account_provisioning
    ON mail_account (status, next_provisioning_at, provisioning_lease_until);
