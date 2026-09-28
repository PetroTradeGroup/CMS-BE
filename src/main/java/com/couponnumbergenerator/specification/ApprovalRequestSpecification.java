package com.couponnumbergenerator.specification;

import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import org.springframework.data.jpa.domain.Specification;

public final class ApprovalRequestSpecification {

    private ApprovalRequestSpecification() {}

    public static Specification<CouponApprovalRequest> withFilters(
            ApprovalRequestType requestType, ApprovalStatus status, String callerLocationCode, String callerUsername) {
        return Specification
                .where(byRequestType(requestType))
                .and(byStatus(status))
                .and(byLocationCode(callerLocationCode))
                .and(byRequestedBy(callerUsername));
    }

    private static Specification<CouponApprovalRequest> byRequestType(ApprovalRequestType requestType) {
        return (root, query, cb) -> requestType == null ? null
                : cb.equal(root.get("requestType"), requestType);
    }

    private static Specification<CouponApprovalRequest> byStatus(ApprovalStatus status) {
        return (root, query, cb) -> status == null ? null
                : cb.equal(root.get("status"), status);
    }

    /** Station-scoped callers (Attendant/Team Leader) only ever see requests bound for their own site. */
    private static Specification<CouponApprovalRequest> byLocationCode(String callerLocationCode) {
        return (root, query, cb) -> callerLocationCode == null ? null
                : cb.equal(root.get("toLocation").get("code"), callerLocationCode);
    }

    /** A plain Attendant (not Team Leader) only sees requests they personally submitted. */
    private static Specification<CouponApprovalRequest> byRequestedBy(String callerUsername) {
        return (root, query, cb) -> callerUsername == null ? null
                : cb.equal(root.get("requestedBy"), callerUsername);
    }
}
