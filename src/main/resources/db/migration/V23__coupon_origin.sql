-- Legacy-imported coupon numbers use the exact same format as system-generated ones (PU + fuel
-- type code + letter + 7 digits), so the number alone can't tell staff which is which — the only
-- prior signal was batch_id being null, which isn't visible on a coupon list/export. origin makes
-- it an explicit, reportable column instead.
ALTER TABLE coupons ADD COLUMN origin VARCHAR(20);

UPDATE coupons SET origin = CASE WHEN batch_id IS NULL THEN 'LEGACY_IMPORT' ELSE 'GENERATED' END;

ALTER TABLE coupons ALTER COLUMN origin SET NOT NULL;

-- Partial, not a full-column index: origin is low-cardinality (two values) and skewed almost
-- entirely toward GENERATED, so a plain index on it would rarely beat a seq scan. The only query
-- that actually needs to be fast is isolating the rare value ("show every legacy coupon"), so
-- only that value is indexed — smaller, cheaper to maintain, and does the one thing it's for.
CREATE INDEX idx_coupon_origin_legacy ON coupons (origin) WHERE origin = 'LEGACY_IMPORT';
