-- Preserve archive action history as detached audit rows after a payroll record is deleted.
ALTER TABLE hr_suite_record_archive_events
    DROP CONSTRAINT IF EXISTS hr_suite_record_archive_events_record_id_fkey;

CREATE TABLE IF NOT EXISTS hr_suite_record_deletion_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_record_id UUID NOT NULL,
    payslip_record_id UUID NULL,
    employee_id UUID NOT NULL,
    period VARCHAR(7) NOT NULL,
    payroll_status VARCHAR(24) NOT NULL,
    payslip_status VARCHAR(24) NULL,
    payroll_amount NUMERIC(14,2) NULL,
    payslip_amount NUMERIC(14,2) NULL,
    deleted_by UUID NOT NULL REFERENCES users(id),
    deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suite_deletion_event_employee_period
    ON hr_suite_record_deletion_events(employee_id, period, deleted_at DESC);

