CREATE TABLE IF NOT EXISTS hr_suite_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_key VARCHAR(32) NOT NULL,
    title VARCHAR(180) NOT NULL,
    employee_id UUID NULL REFERENCES employees(id),
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    period_key VARCHAR(16),
    amount NUMERIC(14,2),
    minutes INTEGER CHECK (minutes IS NULL OR minutes >= 0),
    start_date DATE,
    end_date DATE,
    details TEXT,
    consent BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_suite_date_range CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS idx_suite_module_employee ON hr_suite_records(module_key, employee_id);
CREATE INDEX IF NOT EXISTS idx_suite_module_period ON hr_suite_records(module_key, period_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_suite_payroll_employee_period
    ON hr_suite_records(module_key, employee_id, period_key)
    WHERE module_key = 'payroll';

CREATE TABLE IF NOT EXISTS payroll_profiles (
    employee_id UUID PRIMARY KEY REFERENCES employees(id),
    gross NUMERIC(14,2) NOT NULL CHECK (gross >= 0),
    basic NUMERIC(14,2) NOT NULL CHECK (basic >= 0 AND basic <= gross),
    pf_rate NUMERIC(6,3) NOT NULL CHECK (pf_rate BETWEEN 0 AND 100),
    esi_rate NUMERIC(6,3) NOT NULL CHECK (esi_rate BETWEEN 0 AND 100),
    professional_tax NUMERIC(14,2) NOT NULL CHECK (professional_tax >= 0),
    tds NUMERIC(14,2) NOT NULL CHECK (tds >= 0),
    employer_pf_rate NUMERIC(6,3) NOT NULL CHECK (employer_pf_rate BETWEEN 0 AND 100),
    employer_esi_rate NUMERIC(6,3) NOT NULL CHECK (employer_esi_rate BETWEEN 0 AND 100),
    gratuity NUMERIC(14,2) NOT NULL CHECK (gratuity >= 0),
    policy_version VARCHAR(80),
    updated_by UUID NOT NULL REFERENCES users(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
