ALTER TABLE mail_account_reconciliation
    DROP CONSTRAINT chk_mail_account_reconciliation_drift_type;

ALTER TABLE mail_account_reconciliation
    ADD CONSTRAINT chk_mail_account_reconciliation_drift_type
        CHECK (drift_type IN (
            'NONE',
            'REMOTE_ACCOUNT_MISSING',
            'ENABLED_STATE_MISMATCH',
            'DISPLAY_NAME_MISMATCH',
            'ALIAS_MISMATCH',
            'INSPECTION_FAILED'
        ));
