-- ApprovalStatus.IN_TRANSIT -> TRANSFERSHIPMENT and ApprovalStatus.RECEIVED -> TRANSRECEIPT,
-- matching Petrotrade's own terminology for the two-phase department handoff (Stock ships it,
-- the receiving department signs for it). The status column is unconstrained varchar(20) — no
-- CHECK constraint to rewrite — but existing rows still need their string values updated, since
-- Hibernate (EnumType.STRING) will fail to deserialize a value it no longer recognizes.

UPDATE coupon_approval_requests SET status = 'TRANSFERSHIPMENT' WHERE status = 'IN_TRANSIT';
UPDATE coupon_approval_requests SET status = 'TRANSRECEIPT' WHERE status = 'RECEIVED';