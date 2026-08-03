-- ApprovalRequestResponse had no coupon count at all before the coupons were actually moved
-- (transferredCoupons is only populated by the approve/confirm-receipt call that just did the
-- moving) — a PENDING or TRANSFERSHIPMENT request looked like it covered zero coupons. This
-- fixes it going forward with a count fixed at creation time, not re-derived per stage.

ALTER TABLE coupon_approval_requests
    ADD COLUMN coupon_count integer;

-- Backfill from whichever selection mode each existing request actually used, in order of
-- specificity: pinned coupon numbers (TRANSITION requests and denomination-picked transfers),
-- then a position range, then — for a true whole-batch transfer with neither — the batch's total
-- coupon quantity (best-effort; the exact count at request time is no longer recoverable).
UPDATE coupon_approval_requests car
SET coupon_count = sub.cnt
FROM (
    SELECT approval_request_id, COUNT(*) AS cnt
    FROM coupon_approval_request_coupon_numbers
    GROUP BY approval_request_id
) sub
WHERE car.id = sub.approval_request_id
  AND car.coupon_count IS NULL;

UPDATE coupon_approval_requests
SET coupon_count = range_end - range_start + 1
WHERE coupon_count IS NULL
  AND range_start IS NOT NULL
  AND range_end IS NOT NULL;

UPDATE coupon_approval_requests car
SET coupon_count = cb.quantity
FROM coupon_batches cb
WHERE car.batch_id = cb.id
  AND car.coupon_count IS NULL;

ALTER TABLE coupon_approval_requests
    ALTER COLUMN coupon_count SET NOT NULL;