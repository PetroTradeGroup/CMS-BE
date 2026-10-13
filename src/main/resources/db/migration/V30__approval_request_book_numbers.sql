-- The GIV/GRV list which books an issue covers, alongside the batch positions (V12). Same
-- fixed-at-creation pattern: distinct book numbers of the resolved coupons, stored once.

CREATE TABLE coupon_approval_request_book_numbers (
    approval_request_id bigint NOT NULL REFERENCES coupon_approval_requests (id),
    book_number          integer
);

CREATE INDEX idx_approval_request_book_numbers ON coupon_approval_request_book_numbers (approval_request_id);

-- Backfill from the pinned coupon numbers — every department-handoff transfer (which is what a
-- GIV/GRV is printed for) has them by the time it ships. Coupons without a book number are skipped.
INSERT INTO coupon_approval_request_book_numbers (approval_request_id, book_number)
SELECT DISTINCT cn.approval_request_id, c.book_number
FROM coupon_approval_request_coupon_numbers cn
JOIN coupons c ON c.coupon_number = cn.coupon_number
WHERE c.book_number IS NOT NULL;
