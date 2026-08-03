-- Same motivation as V10's coupon_count: ApprovalRequestResponse had no way to show which
-- denominations a request covers until the coupons were actually moved. Fixed the same way —
-- a breakdown fixed at creation time from the resolved selection, not re-derived per stage.

CREATE TABLE coupon_approval_request_denominations (
    approval_request_id bigint       NOT NULL REFERENCES coupon_approval_requests (id),
    denomination         numeric(10,2),
    count                integer
);

CREATE INDEX idx_approval_request_denominations ON coupon_approval_request_denominations (approval_request_id);

-- Same three-tier backfill priority as V10's coupon_count, just grouped by denomination instead
-- of counted outright: pinned coupon numbers, then position range, then (for a true whole-batch
-- request with neither) every coupon in the batch.
INSERT INTO coupon_approval_request_denominations (approval_request_id, denomination, count)
SELECT cn.approval_request_id, c.denomination, COUNT(*)
FROM coupon_approval_request_coupon_numbers cn
JOIN coupons c ON c.coupon_number = cn.coupon_number
GROUP BY cn.approval_request_id, c.denomination;

INSERT INTO coupon_approval_request_denominations (approval_request_id, denomination, count)
SELECT car.id, c.denomination, COUNT(*)
FROM coupon_approval_requests car
JOIN coupons c ON c.batch_id = car.batch_id
    AND c.batch_sequence BETWEEN car.range_start AND car.range_end
WHERE car.range_start IS NOT NULL
  AND car.range_end IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM coupon_approval_request_denominations d WHERE d.approval_request_id = car.id
  )
GROUP BY car.id, c.denomination;

INSERT INTO coupon_approval_request_denominations (approval_request_id, denomination, count)
SELECT car.id, c.denomination, COUNT(*)
FROM coupon_approval_requests car
JOIN coupons c ON c.batch_id = car.batch_id
WHERE car.batch_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM coupon_approval_request_denominations d WHERE d.approval_request_id = car.id
  )
GROUP BY car.id, c.denomination;