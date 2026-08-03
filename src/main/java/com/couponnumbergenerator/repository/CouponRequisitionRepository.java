package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.model.CouponRequisition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRequisitionRepository extends JpaRepository<CouponRequisition, Long> {

    Page<CouponRequisition> findByStatus(RequisitionStatus status, Pageable pageable);
}