ALTER TABLE app_user
    ADD COLUMN mail_account_limit INT NOT NULL DEFAULT 1;

ALTER TABLE mail_account
    ADD COLUMN email_address_limit INT NOT NULL DEFAULT 1;

CREATE TABLE registration_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    mail_account_limit INT NOT NULL DEFAULT 1,
    email_address_limit INT NOT NULL DEFAULT 1,
    expires_at TIMESTAMP(6) NOT NULL,
    used_by_user_id BIGINT NULL,
    used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_registration_invitation_token_hash
        UNIQUE (token_hash),
    CONSTRAINT chk_registration_invitation_status
        CHECK (status IN ('PENDING', 'USED', 'REVOKED')),
    CONSTRAINT chk_registration_invitation_mail_account_limit
        CHECK (mail_account_limit > 0),
    CONSTRAINT chk_registration_invitation_email_address_limit
        CHECK (email_address_limit > 0),
    CONSTRAINT fk_registration_invitation_used_by_user
        FOREIGN KEY (used_by_user_id)
        REFERENCES app_user (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_registration_invitation_status_expires_at
    ON registration_invitation (status, expires_at);
