ALTER TABLE user_status_audit
    DROP CONSTRAINT chk_user_status_audit_action;

ALTER TABLE user_status_audit
    ADD CONSTRAINT chk_user_status_audit_action
        CHECK (action IN (
            'DISABLED',
            'ENABLED',
            'FORCED_LOGOUT',
            'PASSWORD_CHANGED',
            'PASSWORD_RESET_BY_ADMIN',
            'MAIL_ACCOUNT_PROVISIONING_RETRY_REQUESTED',
            'MAIL_ACCOUNT_DRIFT_REPAIR_REQUESTED'
        ));
