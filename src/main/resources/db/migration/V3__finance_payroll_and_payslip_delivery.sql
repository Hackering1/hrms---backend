ALTER TABLE payroll_profiles
    ADD COLUMN IF NOT EXISTS hra NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (hra >= 0),
    ADD COLUMN IF NOT EXISTS lta NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (lta >= 0);

INSERT INTO roles (name, description)
VALUES ('FINANCE_ADMIN', 'Controls payroll profiles, approval, and payslip delivery')
ON CONFLICT (name) DO NOTHING;
