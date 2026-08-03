-- Printers issue coupons by fuel volume, not by unit count: an order for 50L of petrol
-- becomes a mix of denominations (e.g. one 20L + two 10L + two 5L coupon). Each coupon now
-- carries the litres it's redeemable for, and each batch records the litres originally
-- requested so it can be validated against the sum of its coupons' denominations.

ALTER TABLE coupons ADD COLUMN denomination numeric(10,2);

-- Legacy coupons predate the denomination concept; their real litre value is not recoverable,
-- so they are backfilled with 0 as an explicit "unknown" marker rather than a fabricated value.
UPDATE coupons SET denomination = 0 WHERE denomination IS NULL;

ALTER TABLE coupons ALTER COLUMN denomination SET NOT NULL;

ALTER TABLE coupon_batches ADD COLUMN target_quantity numeric(10,2);

-- Legacy batches predate the target-quantity concept; backfill with the existing coupon
-- count so the column is never null (also an explicit "unknown litres" marker).
UPDATE coupon_batches SET target_quantity = quantity WHERE target_quantity IS NULL;

ALTER TABLE coupon_batches ALTER COLUMN target_quantity SET NOT NULL;