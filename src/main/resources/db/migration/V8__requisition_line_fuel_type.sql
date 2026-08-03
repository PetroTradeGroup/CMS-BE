-- A requisition line only carried a denomination, not a fuel type, so a department couldn't
-- distinguish "20L petrol" from "20L diesel" on the same requisition, and fulfilment couldn't
-- tell whether the named batch actually matched what the line asked for.

ALTER TABLE requisition_lines
    ADD COLUMN fuel_type_id bigint REFERENCES fuel_types (id);

-- Backfill existing lines from the coupons actually delivered against them: each requisition's
-- fulfil() calls are recorded as coupon_approval_requests linked back via requisition_id, and the
-- coupons they moved (coupon_approval_request_coupon_numbers -> coupons) already carry the correct
-- fuel_type_id. A denomination uniquely identifies which line a delivery belongs to within one
-- requisition, so requisition_id + denomination is enough to attribute the right fuel type.
UPDATE requisition_lines rl
SET fuel_type_id = c.fuel_type_id
FROM coupon_approval_requests car
JOIN coupon_approval_request_coupon_numbers cn ON cn.approval_request_id = car.id
JOIN coupons c ON c.coupon_number = cn.coupon_number
WHERE car.requisition_id = rl.requisition_id
  AND c.denomination = rl.denomination
  AND rl.fuel_type_id IS NULL;

-- Any line still unresolved (raised but never fulfilled, so no delivered coupons to infer from)
-- falls back to Petrol — a guess, since there's no evidence of what fuel type was actually meant.
UPDATE requisition_lines
SET fuel_type_id = (SELECT id FROM fuel_types WHERE type_code = '002')
WHERE fuel_type_id IS NULL;

ALTER TABLE requisition_lines
    ALTER COLUMN fuel_type_id SET NOT NULL;

CREATE INDEX idx_requisition_line_fuel_type_id ON requisition_lines (fuel_type_id);