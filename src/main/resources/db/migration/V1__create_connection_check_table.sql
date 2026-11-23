-- Initial migration: a minimal table used to verify the MySQL connection and Flyway setup.
CREATE TABLE connection_check (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    note       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
