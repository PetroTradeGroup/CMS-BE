-- Supports CouponRepository.findIssuableForSale: pick IN_STOCK coupons of a location + fuel
-- type + denomination in the stock department, ordered by batch sequence / book / position
-- (see §11.2 of docs/erp-sales-integration-design.md). Partial index — only IN_STOCK rows are
-- ever eligible to sell, so the rest are dead weight in this index.

CREATE INDEX idx_coupon_issuable_for_sale
    ON coupons (current_location_id, fuel_type_id, denomination, current_department_id,
                book_number, batch_sequence)
    WHERE status = 'IN_STOCK';
