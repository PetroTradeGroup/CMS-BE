-- The vehicle a REDEMPTION-type approval request was recorded against, asserted by the
-- attendant at submission time.
ALTER TABLE coupon_approval_requests ADD COLUMN car_registration_number varchar(20);
