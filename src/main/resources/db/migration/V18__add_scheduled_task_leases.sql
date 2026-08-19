CREATE TABLE scheduled_task_lease (
    task_name VARCHAR(100) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    lease_until TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (task_name)
);
