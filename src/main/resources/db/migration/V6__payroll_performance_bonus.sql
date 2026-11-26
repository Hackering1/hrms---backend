ALTER TABLE payroll_profiles
    ADD COLUMN IF NOT EXISTS performance_bonus_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS performance_bonus NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (performance_bonus >= 0);

