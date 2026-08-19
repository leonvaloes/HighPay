CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_processed_events_payment_id
    ON processed_events (payment_id);