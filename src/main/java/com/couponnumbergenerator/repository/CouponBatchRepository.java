package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.CouponBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CouponBatchRepository extends JpaRepository<CouponBatch, Long>, JpaSpecificationExecutor<CouponBatch> {
}