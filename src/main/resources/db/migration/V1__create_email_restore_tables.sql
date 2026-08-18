CREATE TABLE email_restore_record (
    account_id VARCHAR(255) NOT NULL,
    email_id VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, email_id)
);

CREATE INDEX idx_email_restore_record_deleted_at
    ON email_restore_record (deleted_at);

CREATE TABLE email_restore_mailbox (
    account_id VARCHAR(255) NOT NULL,
    email_id VARCHAR(255) NOT NULL,
    mailbox_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (account_id, email_id, mailbox_id),
    CONSTRAINT fk_email_restore_mailbox_record
        FOREIGN KEY (account_id, email_id)
        REFERENCES email_restore_record (account_id, email_id)
        ON DELETE CASCADE
);
