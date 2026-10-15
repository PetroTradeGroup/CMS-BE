package com.couponnumbergenerator.specification;

import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ApprovalRequestSpecification {

    private ApprovalRequestSpecification() {}

    public static Specification<CouponApprovalRequest> withFilters(
            ApprovalRequestType requestType, ApprovalStatus status, String callerLocationCode, String callerUsername,
            String requisitionRaisedBy) {
        return Specification
                .where(byRequestType(requestType))
                .and(byStatus(status))
                .and(byLocationCode(callerLocationCode))
                .and(byRequestedBy(callerUsername))
                .and(byRequisitionRaisedBy(requisitionRaisedBy));
    }

    /**
     * The redemption log: REDEMPTION requests submitted in [start, end), optionally at one site, one company's sites,
     * by one attendant, or holding a coupon of one type. Any filter left null is not applied.
     */
    public static Specification<CouponApprovalRequest> redemptionLog(
            LocalDateTime start, LocalDateTime end, Long locationId, Long companyId, String requestedBy,
            CouponType couponType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("requestType"), ApprovalRequestType.REDEMPTION));
            predicates.add(cb.greaterThanOrEqualTo(root.get("requestedAt"), start));
            predicates.add(cb.lessThan(root.get("requestedAt"), end));
            if (locationId != null) predicates.add(cb.equal(root.get("toLocation").get("id"), locationId));
            if (companyId != null) predicates.add(cb.equal(root.get("toLocation").get("company").get("id"), companyId));
            if (requestedBy != null) predicates.add(cb.equal(root.get("requestedBy"), requestedBy));
            if (couponType != null) {
                // Redemptions holding at least one coupon of that type.
                Subquery<Long> sub = query.subquery(Long.class);
                Root<CouponApprovalRequest> r = sub.correlate(root);
                Root<Coupon> c = sub.from(Coupon.class);
                sub.select(cb.literal(1L)).where(
                        cb.equal(c.get("couponNumber"), r.join("couponNumbers")),
                        cb.equal(c.get("couponType"), couponType));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** A Sales Clerk only sees the issues raised against their own requisitions — the ones they receive. */
    private static Specification<CouponApprovalRequest> byRequisitionRaisedBy(String username) {
        return (root, query, cb) -> username == null ? null
                : cb.equal(root.get("requisition").get("requestedBy"), username);
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
