-- Books: the print vendor binds every 100 consecutive coupons of one denomination (in the
-- order of our print CSV) into a physical book. book_number records which book of its batch
-- a coupon belongs to (1-indexed). Null for coupons generated before books existed, for
-- digital coupons, and for lines that didn't form whole books.
ALTER TABLE coupons ADD COLUMN book_number INTEGER;

CREATE INDEX idx_coupon_batch_book ON coupons (batch_id, book_number);