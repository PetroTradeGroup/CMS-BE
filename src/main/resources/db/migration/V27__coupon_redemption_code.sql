-- Virtual (DIGITAL) coupons get a 7-character redemption code: the customer's secret, typed at the
-- station instead of scanning the QR. The coupon number isn't secret (it shows in lists and
-- exports), so typing it alone must not be enough to redeem a virtual coupon. NULL for physical
-- coupons, which the station physically collects.
ALTER TABLE coupons ADD COLUMN redemption_code varchar(7);

CREATE UNIQUE INDEX uq_coupons_redemption_code ON coupons (redemption_code) WHERE redemption_code IS NOT NULL;
