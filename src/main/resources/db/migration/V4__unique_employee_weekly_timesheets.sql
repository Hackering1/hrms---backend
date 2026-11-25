-- Keep previously stored HR suite rows intact. If duplicate weekly sheets
-- already exist from an earlier deployment, leave them for review and allow
-- startup to proceed; the service still rejects new duplicates.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM hr_suite_records
        WHERE module_key = 'timesheets'
        GROUP BY employee_id, period_key
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_suite_timesheet_employee_period
            ON hr_suite_records(module_key, employee_id, period_key)
            WHERE module_key = 'timesheets';
    END IF;
END $$;
