-- Every site belongs to a company: our own (Petrotrade) or a third party that redeems on our behalf
-- (e.g. Redan). Existing sites all start under Petrotrade.
CREATE TABLE companies (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

INSERT INTO companies (code, name) VALUES ('PETROTRADE', 'Petrotrade');

ALTER TABLE locations ADD COLUMN company_id BIGINT REFERENCES companies (id);
UPDATE locations SET company_id = (SELECT id FROM companies WHERE code = 'PETROTRADE');
ALTER TABLE locations ALTER COLUMN company_id SET NOT NULL;
CREATE INDEX idx_locations_company ON locations (company_id);
