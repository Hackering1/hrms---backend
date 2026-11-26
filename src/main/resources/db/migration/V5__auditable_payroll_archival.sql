ALTER TABLE hr_suite_records
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS archived_by UUID NULL REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_suite_module_archived
    ON hr_suite_records(module_key, archived_at);

CREATE TABLE IF NOT EXISTS hr_suite_record_archive_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id UUID NOT NULL REFERENCES hr_suite_records(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('ARCHIVED', 'RESTORED')),
    actor_id UUID NOT NULL REFERENCES users(id),
    acted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suite_archive_event_record_time
    ON hr_suite_record_archive_events(record_id, acted_at DESC);

