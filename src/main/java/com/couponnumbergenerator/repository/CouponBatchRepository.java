package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.CouponBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CouponBatchRepository extends JpaRepository<CouponBatch, Long>, JpaSpecificationExecutor<CouponBatch> {

    /** All batches of a fuel type, oldest first — the FIFO order auto-fulfillment draws stock in. */
    List<CouponBatch> findByFuelTypeIdOrderByCreatedAtAsc(Long fuelTypeId);
}