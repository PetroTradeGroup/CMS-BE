-- Navision cross-reference stamped on a REDEMPTION-type approval request once it's posted
-- (Duty 6: "Post Redeemed/Scanned Coupons ... with document number").
ALTER TABLE coupon_approval_requests ADD COLUMN document_number varchar(50);
