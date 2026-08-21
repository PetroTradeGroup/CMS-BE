-- AD-3: department-scoped authorization needs the *origin* department pinned at request
-- creation, separately from the existing to_department_id (the destination, used by
-- confirm-receipt). Only department-handoff TRANSFER requests ever populate this column —
-- TRANSITION and REDEMPTION requests never move department, so it stays null for those, same
-- as to_department_id already does today.

ALTER TABLE coupon_approval_requests
    ADD COLUMN from_department_id bigint REFERENCES departments (id);

CREATE INDEX idx_approval_from_department ON coupon_approval_requests (from_department_id);
