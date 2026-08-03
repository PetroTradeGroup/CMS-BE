package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.CouponMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Movements are an immutable audit log: this repository must only ever be used to insert and read.
 */
public interface CouponMovementRepository extends JpaRepository<CouponMovement, Long> {

    List<CouponMovement> findByCouponIdOrderByCreatedAtAsc(Long couponId);
}