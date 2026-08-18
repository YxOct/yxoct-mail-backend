CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_app_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE mail_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stalwart_account_id VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROVISIONING',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_mail_account_stalwart_account_id
        UNIQUE (stalwart_account_id),
    CONSTRAINT chk_mail_account_status
        CHECK (status IN ('PROVISIONING', 'ACTIVE', 'FAILED', 'DISABLED'))
);

CREATE TABLE email_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mail_account_id BIGINT NOT NULL,
    address VARCHAR(320) NOT NULL,
    normalized_address VARCHAR(320) NOT NULL,
    address_type VARCHAR(16) NOT NULL DEFAULT 'PRIMARY',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_email_address_normalized_address
        UNIQUE (normalized_address),
    CONSTRAINT chk_email_address_type
        CHECK (address_type IN ('PRIMARY', 'ALIAS')),
    CONSTRAINT fk_email_address_mail_account
        FOREIGN KEY (mail_account_id)
        REFERENCES mail_account (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_email_address_mail_account_id
    ON email_address (mail_account_id);

CREATE TABLE user_mail_account (
    user_id BIGINT NOT NULL,
    mail_account_id BIGINT NOT NULL,
    account_role VARCHAR(16) NOT NULL DEFAULT 'OWNER',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, mail_account_id),
    CONSTRAINT chk_user_mail_account_role
        CHECK (account_role IN ('OWNER', 'MEMBER')),
    CONSTRAINT fk_user_mail_account_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_mail_account_mail_account
        FOREIGN KEY (mail_account_id)
        REFERENCES mail_account (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_user_mail_account_mail_account_id
    ON user_mail_account (mail_account_id);
