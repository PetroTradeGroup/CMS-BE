-- Optimistic locking to prevent double-redemption races from concurrent scans across sites.
ALTER TABLE coupons ADD COLUMN version bigint NOT NULL DEFAULT 0;
