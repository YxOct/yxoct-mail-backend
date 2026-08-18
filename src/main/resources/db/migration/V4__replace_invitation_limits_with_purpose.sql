ALTER TABLE registration_invitation
    ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'REGISTRATION' AFTER status;

ALTER TABLE registration_invitation
    DROP CONSTRAINT chk_registration_invitation_mail_account_limit;

ALTER TABLE registration_invitation
    DROP CONSTRAINT chk_registration_invitation_email_address_limit;

ALTER TABLE registration_invitation
    DROP COLUMN mail_account_limit;

ALTER TABLE registration_invitation
    DROP COLUMN email_address_limit;

ALTER TABLE app_user
    DROP COLUMN mail_account_limit;

ALTER TABLE mail_account
    DROP COLUMN email_address_limit;

ALTER TABLE registration_invitation
    ADD CONSTRAINT chk_registration_invitation_purpose
        CHECK (purpose IN ('REGISTRATION', 'EMAIL_ADDRESS'));
