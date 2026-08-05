package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponApprovalRequestRepository extends JpaRepository<CouponApprovalRequest, Long> {

    Page<CouponApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

    Page<CouponApprovalRequest> findByRequestType(ApprovalRequestType requestType, Pageable pageable);

    Page<CouponApprovalRequest> findByRequestTypeAndStatus(ApprovalRequestType requestType, ApprovalStatus status, Pageable pageable);
}
