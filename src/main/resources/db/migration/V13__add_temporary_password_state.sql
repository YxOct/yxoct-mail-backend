ALTER TABLE app_user
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_status_audit
    MODIFY COLUMN action VARCHAR(32) NOT NULL;

ALTER TABLE user_status_audit
    DROP CONSTRAINT chk_user_status_audit_action;

ALTER TABLE user_status_audit
    ADD CONSTRAINT chk_user_status_audit_action
        CHECK (action IN ('DISABLED', 'ENABLED', 'FORCED_LOGOUT', 'PASSWORD_CHANGED', 'PASSWORD_RESET_BY_ADMIN'));
