-- Same motivation as V10/V11: Stock needs the batch positions a request covers to match against
-- a physical coupon book during a count, and that needs to be visible at every stage, not just
-- once the coupons have actually moved. Fixed the same way — a list fixed at creation time from
-- the resolved selection, not re-derived per stage.

CREATE TABLE coupon_approval_request_batch_sequences (
    approval_request_id bigint NOT NULL REFERENCES coupon_approval_requests (id),
    batch_sequence       integer
);

CREATE INDEX idx_approval_request_batch_sequences ON coupon_approval_request_batch_sequences (approval_request_id);

-- Same three-tier backfill priority as V10/V11: pinned coupon numbers, then position range, then
-- (for a true whole-batch request with neither) every coupon in the batch. Coupons with no
-- batch_sequence (generated before position tracking existed) are left out, same as at runtime.
INSERT INTO coupon_approval_request_batch_sequences (approval_request_id, batch_sequence)
SELECT cn.approval_request_id, c.batch_sequence
FROM coupon_approval_request_coupon_numbers cn
JOIN coupons c ON c.coupon_number = cn.coupon_number
WHERE c.batch_sequence IS NOT NULL;

INSERT INTO coupon_approval_request_batch_sequences (approval_request_id, batch_sequence)
SELECT car.id, c.batch_sequence
FROM coupon_approval_requests car
JOIN coupons c ON c.batch_id = car.batch_id
    AND c.batch_sequence BETWEEN car.range_start AND car.range_end
WHERE car.range_start IS NOT NULL
  AND car.range_end IS NOT NULL
  AND c.batch_sequence IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM coupon_approval_request_batch_sequences s WHERE s.approval_request_id = car.id
  );

INSERT INTO coupon_approval_request_batch_sequences (approval_request_id, batch_sequence)
SELECT car.id, c.batch_sequence
FROM coupon_approval_requests car
JOIN coupons c ON c.batch_id = car.batch_id
WHERE car.batch_id IS NOT NULL
  AND c.batch_sequence IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM coupon_approval_request_batch_sequences s WHERE s.approval_request_id = car.id
  );