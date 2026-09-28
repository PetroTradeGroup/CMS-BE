package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CouponApprovalRequestRepository
        extends JpaRepository<CouponApprovalRequest, Long>, JpaSpecificationExecutor<CouponApprovalRequest> {

    Page<CouponApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

    Page<CouponApprovalRequest> findByRequestType(ApprovalRequestType requestType, Pageable pageable);

    Page<CouponApprovalRequest> findByRequestTypeAndStatus(ApprovalRequestType requestType, ApprovalStatus status, Pageable pageable);

    /** Which of {@code couponNumbers} already sit in a request of this type and status (e.g. a PENDING REDEMPTION). */
    @Query("SELECT DISTINCT n FROM CouponApprovalRequest r JOIN r.couponNumbers n "
            + "WHERE r.requestType = :type AND r.status = :status AND n IN :couponNumbers")
    List<String> findCouponNumbersInRequests(@Param("type") ApprovalRequestType type,
                                             @Param("status") ApprovalStatus status,
                                             @Param("couponNumbers") Collection<String> couponNumbers);
}
