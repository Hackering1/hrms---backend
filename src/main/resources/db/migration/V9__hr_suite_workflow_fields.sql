ALTER TABLE hr_suite_records
    ADD COLUMN IF NOT EXISTS category_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS priority_key VARCHAR(16),
    ADD COLUMN IF NOT EXISTS reviewed_by UUID,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reviewer_remarks TEXT,
    ADD COLUMN IF NOT EXISTS consent_policy_version VARCHAR(64);
