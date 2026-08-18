ALTER TABLE registration_invitation
    ADD COLUMN created_by_user_id BIGINT NULL;

ALTER TABLE registration_invitation
    ADD COLUMN revoked_by_user_id BIGINT NULL;

ALTER TABLE registration_invitation
    ADD COLUMN revoked_at TIMESTAMP(6) NULL;

ALTER TABLE registration_invitation
    ADD CONSTRAINT fk_registration_invitation_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES app_user (id)
        ON DELETE SET NULL;

ALTER TABLE registration_invitation
    ADD CONSTRAINT fk_registration_invitation_revoked_by_user
        FOREIGN KEY (revoked_by_user_id)
        REFERENCES app_user (id)
        ON DELETE SET NULL;
