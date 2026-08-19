CREATE TABLE mail_account_reconciliation (
    mail_account_id BIGINT NOT NULL,
    drift_type VARCHAR(64) NOT NULL,
    last_error VARCHAR(64) NULL,
    checked_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (mail_account_id),
    CONSTRAINT chk_mail_account_reconciliation_drift_type
        CHECK (drift_type IN (
            'NONE',
            'REMOTE_ACCOUNT_MISSING',
            'ENABLED_STATE_MISMATCH',
            'INSPECTION_FAILED'
        )),
    CONSTRAINT fk_mail_account_reconciliation_account
        FOREIGN KEY (mail_account_id)
        REFERENCES mail_account (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_mail_account_reconciliation_drift
    ON mail_account_reconciliation (drift_type, checked_at, mail_account_id);
