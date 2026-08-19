ALTER TABLE processed_events
    ADD COLUMN provider_status VARCHAR(30),
    ADD COLUMN provider_transaction_id VARCHAR(100);