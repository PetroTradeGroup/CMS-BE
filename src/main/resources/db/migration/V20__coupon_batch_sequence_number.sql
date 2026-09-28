-- Batch sequence number: a 1-based counter per fuel type (batch 1, 2, 3… of PETROL, tracked
-- separately from DIESEL). This is the canonical order coupon stock is sold in — oldest
-- sequence number first — making explicit the ordering that was previously implicit in
-- created_at / the timestamp inside batch_number. See §11.3 of
-- docs/erp-sales-integration-design.md.

ALTER TABLE coupon_batches ADD COLUMN sequence_number bigint;

-- Backfill: number existing batches per fuel type in creation order (created_at, then id as
-- a stable tie-break).
WITH ordered AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY fuel_type_id ORDER BY created_at, id) AS rn
    FROM coupon_batches
)
UPDATE coupon_batches b
SET sequence_number = ordered.rn
FROM ordered
WHERE b.id = ordered.id;

ALTER TABLE coupon_batches ALTER COLUMN sequence_number SET NOT NULL;

CREATE UNIQUE INDEX idx_batch_fuel_type_sequence
    ON coupon_batches (fuel_type_id, sequence_number);

-- The per-fuel-type counter lives on coupon_sequences — generation already loads that row
-- FOR UPDATE in the same transaction that inserts the batch, so the increment is race-safe
-- without a new lock. Seed it to the current batch count so the next batch continues the run.
ALTER TABLE coupon_sequences ADD COLUMN batch_sequence_number bigint NOT NULL DEFAULT 0;

UPDATE coupon_sequences cs
SET batch_sequence_number = (
    SELECT count(*) FROM coupon_batches b WHERE b.fuel_type_id = cs.fuel_type_id
);
