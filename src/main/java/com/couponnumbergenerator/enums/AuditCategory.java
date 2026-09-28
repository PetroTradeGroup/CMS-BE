package com.couponnumbergenerator.enums;

/** What kind of event a row in the system-wide audit feed represents. */
public enum AuditCategory {
    /** A coupon lifecycle event — sourced from {@link com.couponnumbergenerator.model.CouponMovement}. */
    COUPON_LIFECYCLE,
    /** A supervisor decision on an approval request (including rejections, which move no coupon and so leave no CouponMovement of their own). */
    APPROVAL_DECISION,
    /** A denied/unauthenticated request, or an allowed mutating one — sourced from {@link com.couponnumbergenerator.model.SecurityAuditEvent}. */
    SECURITY
}
