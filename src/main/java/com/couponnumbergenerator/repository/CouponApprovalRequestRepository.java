package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.repository.projection.RedemptionLogTotalsRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CouponApprovalRequestRepository
        extends JpaRepository<CouponApprovalRequest, Long>, JpaSpecificationExecutor<CouponApprovalRequest> {

    Page<CouponApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

    Page<CouponApprovalRequest> findByRequestType(ApprovalRequestType requestType, Pageable pageable);

    Page<CouponApprovalRequest> findByRequestTypeAndStatus(ApprovalRequestType requestType, ApprovalStatus status, Pageable pageable);

    /**
     * Totals behind the redemption log: same filters as {@code ApprovalRequestSpecification.redemptionLog}, over the
     * whole range rather than one page. REJECTED redemptions are left out — their coupons were never redeemed.
     * Counted from the redeemed coupons themselves, so a coupon type filter is exact even for a mixed redemption.
     */
    @Query("""
            SELECT COUNT(DISTINCT r.id) AS redemptions,
                   COUNT(c) AS coupons,
                   COALESCE(SUM(c.denomination), 0) AS litres
            FROM CouponApprovalRequest r JOIN r.couponNumbers n JOIN Coupon c ON c.couponNumber = n
            WHERE r.requestType = com.couponnumbergenerator.enums.ApprovalRequestType.REDEMPTION
              AND r.status <> com.couponnumbergenerator.enums.ApprovalStatus.REJECTED
              AND r.requestedAt >= :start AND r.requestedAt < :end
              AND (:locationId IS NULL OR r.toLocation.id = :locationId)
              AND (:companyId IS NULL OR r.toLocation.company.id = :companyId)
              AND (:requestedBy IS NULL OR r.requestedBy = :requestedBy)
              AND (:couponType IS NULL OR c.couponType = :couponType)
            """)
    RedemptionLogTotalsRow summarizeRedemptionLog(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                                 @Param("locationId") Long locationId, @Param("companyId") Long companyId,
                                                 @Param("requestedBy") String requestedBy,
                                                 @Param("couponType") CouponType couponType);

    /** Which of {@code couponNumbers} already sit in a request of this type and status (e.g. a PENDING REDEMPTION). */
    @Query("SELECT DISTINCT n FROM CouponApprovalRequest r JOIN r.couponNumbers n "
            + "WHERE r.requestType = :type AND r.status = :status AND n IN :couponNumbers")
    List<String> findCouponNumbersInRequests(@Param("type") ApprovalRequestType type,
                                             @Param("status") ApprovalStatus status,
                                             @Param("couponNumbers") Collection<String> couponNumbers);
}
