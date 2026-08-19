ALTER TABLE processed_events
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PROCESSED';
CREATE INDEX idx_processed_events_status
    ON processed_events (status);