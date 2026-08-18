CREATE TABLE refresh_token_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_session_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_session_user_id
    ON refresh_token_session (user_id);

CREATE INDEX idx_refresh_token_session_expires_at
    ON refresh_token_session (expires_at);
