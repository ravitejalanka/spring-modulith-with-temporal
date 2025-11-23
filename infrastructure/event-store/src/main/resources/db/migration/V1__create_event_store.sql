-- Event Store Table
CREATE TABLE event_store (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    metadata TEXT,
    version BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_aggregate_version UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_aggregate ON event_store(aggregate_id, version);
CREATE INDEX idx_aggregate_type ON event_store(aggregate_type);
CREATE INDEX idx_occurred_at ON event_store(occurred_at);
