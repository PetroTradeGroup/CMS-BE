-- Multiple partner banks: each purchase belongs to the bank that made it, identified by its Keycloak
-- client id (the token's azp claim). A bank's transaction reference only has to be unique within
-- that bank, so two banks can both send "TXN-1001".
ALTER TABLE bank_purchases ADD COLUMN bank_code varchar(50);

-- Purchases made before this change all came from the only bank client that existed.
UPDATE bank_purchases SET bank_code = 'bank-integration' WHERE bank_code IS NULL;

ALTER TABLE bank_purchases ALTER COLUMN bank_code SET NOT NULL;

ALTER TABLE bank_purchases DROP CONSTRAINT bank_purchases_bank_reference_key;

ALTER TABLE bank_purchases ADD CONSTRAINT uq_bank_purchases_bank_reference UNIQUE (bank_code, bank_reference);
