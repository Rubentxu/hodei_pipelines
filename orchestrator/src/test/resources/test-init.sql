-- Test initialization script for database tests
-- Creates a simple test table to verify connection

CREATE TABLE IF NOT EXISTS test_connection (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO test_connection DEFAULT VALUES;