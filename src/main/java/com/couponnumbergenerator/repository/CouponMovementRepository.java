package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.CouponMovement;
import com.couponnumbergenerator.repository.projection.RedemptionSummaryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Movements are an immutable audit log: this repository must only ever be used to insert and read.
 */
public interface CouponMovementRepository extends JpaRepository<CouponMovement, Long> {

    List<CouponMovement> findByCouponIdOrderByCreatedAtAsc(Long couponId);

    @Query("""
            SELECT ft.id AS fuelTypeId, ft.name AS fuelTypeName,
                   c.denomination AS denomination, COUNT(c) AS count, SUM(c.denomination) AS litres
            FROM CouponMovement m
            JOIN m.coupon c
            JOIN c.fuelType ft
            WHERE m.movementType = com.couponnumbergenerator.enums.MovementType.REDEMPTION
              AND m.createdAt >= :start AND m.createdAt < :end
              AND (:locationId IS NULL OR m.toLocation.id = :locationId)
            GROUP BY ft.id, ft.name, c.denomination
            ORDER BY ft.name, c.denomination
            """)
    List<RedemptionSummaryRow> summarizeRedemptions(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    @Param("locationId") Long locationId);
}
