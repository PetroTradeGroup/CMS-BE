-- Coupons should expire a configurable period after generation instead of relying on the
-- caller to pass an expiry date on every request. The period lives in runtime config
-- (like max_count / max_page_size) so admins set it once; 365 days = the business default
-- of "valid for a year from creation". An explicit expiryDate on a generate request still
-- overrides the computed default.

ALTER TABLE bulk_generation_config ADD COLUMN default_validity_days integer NOT NULL DEFAULT 365;