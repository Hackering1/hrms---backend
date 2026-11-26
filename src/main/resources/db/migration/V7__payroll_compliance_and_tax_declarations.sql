ALTER TABLE payroll_profiles
    ADD COLUMN IF NOT EXISTS dearness_allowance NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (dearness_allowance >= 0),
    ADD COLUMN IF NOT EXISTS pf_contribution_mode VARCHAR(20) NOT NULL DEFAULT 'TWELVE_PERCENT',
    ADD COLUMN IF NOT EXISTS group_medical_insurance NUMERIC(14,2) NOT NULL DEFAULT 1250 CHECK (group_medical_insurance >= 0),
    ADD COLUMN IF NOT EXISTS tax_regime VARCHAR(8) NOT NULL DEFAULT 'NEW',
    ADD COLUMN IF NOT EXISTS tax_resident BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS tax_previous_employer_income NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_previous_employer_income >= 0),
    ADD COLUMN IF NOT EXISTS tax_previous_employer_tds NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_previous_employer_tds >= 0),
    ADD COLUMN IF NOT EXISTS tax_other_annual_income NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_other_annual_income >= 0),
    ADD COLUMN IF NOT EXISTS tax_rent_paid_annual NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_rent_paid_annual >= 0),
    ADD COLUMN IF NOT EXISTS tax_hra_metro BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tax_landlord_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS tax_landlord_pan VARCHAR(10),
    ADD COLUMN IF NOT EXISTS tax_landlord_address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS tax_lta_claim_annual NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_lta_claim_annual >= 0),
    ADD COLUMN IF NOT EXISTS tax_home_loan_interest_annual NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_home_loan_interest_annual >= 0),
    ADD COLUMN IF NOT EXISTS tax_lender_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS tax_lender_pan VARCHAR(10),
    ADD COLUMN IF NOT EXISTS tax_80c_annual NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_80c_annual >= 0),
    ADD COLUMN IF NOT EXISTS tax_80d_annual NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_80d_annual >= 0),
    ADD COLUMN IF NOT EXISTS tax_nps_annual NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_nps_annual >= 0);

ALTER TABLE payroll_profiles
    ADD CONSTRAINT payroll_profiles_pf_contribution_mode_check
        CHECK (pf_contribution_mode IN ('TWELVE_PERCENT', 'FLAT_3000')),
    ADD CONSTRAINT payroll_profiles_tax_regime_check
        CHECK (tax_regime IN ('NEW', 'OLD'));

