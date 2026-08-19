ALTER TABLE app_user
    ADD COLUMN disabled_at TIMESTAMP(6) NULL;

ALTER TABLE app_user
    ADD COLUMN disabled_by_user_id BIGINT NULL;

ALTER TABLE app_user
    ADD COLUMN disabled_reason VARCHAR(500) NULL;

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_disabled_by_user
        FOREIGN KEY (disabled_by_user_id)
        REFERENCES app_user (id)
        ON DELETE SET NULL;

CREATE TABLE user_status_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NULL,
    operated_by_user_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_user_status_audit_action
        CHECK (action IN ('DISABLED', 'ENABLED')),
    CONSTRAINT fk_user_status_audit_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_status_audit_operated_by_user
        FOREIGN KEY (operated_by_user_id)
        REFERENCES app_user (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_user_status_audit_user_created
    ON user_status_audit (user_id, created_at, id);
