-- Requisitions are now approved twice before Stocks issues against them: first by Commercial
-- (Manager or Rep), then by the Stocks Controller. Status gains COMMERCIAL_APPROVED and APPROVED
-- (varchar(20), no check constraint — nothing to alter there). Existing PENDING requisitions simply
-- wait for Commercial's approval under the new flow.
ALTER TABLE coupon_requisitions ADD COLUMN commercial_approved_by varchar(100);
ALTER TABLE coupon_requisitions ADD COLUMN commercial_approved_at timestamp;
ALTER TABLE coupon_requisitions ADD COLUMN stocks_approved_by varchar(100);
ALTER TABLE coupon_requisitions ADD COLUMN stocks_approved_at timestamp;
