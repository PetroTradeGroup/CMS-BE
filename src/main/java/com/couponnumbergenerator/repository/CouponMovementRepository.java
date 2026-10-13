package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.model.CouponMovement;
import com.couponnumbergenerator.repository.projection.CouponActivityRow;
import com.couponnumbergenerator.repository.projection.StatusFlowRow;
import com.couponnumbergenerator.repository.projection.RedemptionByAttendantRow;
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
            LEFT JOIN CouponApprovalRequest a ON a.id = m.referenceId
            WHERE m.movementType = com.couponnumbergenerator.enums.MovementType.REDEMPTION
              AND m.createdAt >= :start AND m.createdAt < :end
              AND (:locationId IS NULL OR m.toLocation.id = :locationId)
              AND (:requestedBy IS NULL OR a.requestedBy = :requestedBy)
              AND (:fuelTypeId IS NULL OR ft.id = :fuelTypeId)
            GROUP BY ft.id, ft.name, c.denomination
            ORDER BY ft.name, c.denomination
            """)
    List<RedemptionSummaryRow> summarizeRedemptions(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    @Param("locationId") Long locationId,
                                                    @Param("requestedBy") String requestedBy,
                                                    @Param("fuelTypeId") Long fuelTypeId);

    /**
     * Coupons that moved into each status within [start, end) — REASSIGNMENT rows are skipped since the
     * status didn't change. A coupon that enters the same status twice in the range (e.g. returned to
     * stock) counts twice: this is activity, not a balance.
     */
    @Query("""
            SELECT m.toStatus AS status, ft.id AS fuelTypeId, ft.name AS fuelTypeName,
                   c.denomination AS denomination, COUNT(c) AS count, SUM(c.denomination) AS litres
            FROM CouponMovement m
            JOIN m.coupon c
            JOIN c.fuelType ft
            WHERE m.movementType <> com.couponnumbergenerator.enums.MovementType.REASSIGNMENT
              AND m.createdAt >= :start AND m.createdAt < :end
              AND (:status IS NULL OR m.toStatus = :status)
              AND (:locationId IS NULL OR m.toLocation.id = :locationId)
              AND (:fuelTypeId IS NULL OR ft.id = :fuelTypeId)
            GROUP BY m.toStatus, ft.id, ft.name, c.denomination
            ORDER BY m.toStatus, ft.name, c.denomination
            """)
    List<CouponActivityRow> summarizeActivity(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("status") CouponStatus status,
                                              @Param("locationId") Long locationId,
                                              @Param("fuelTypeId") Long fuelTypeId);

    /**
     * Where every coupon stood just before {@code end}: each coupon's latest movement before then (max id —
     * ids follow insert order) gives its status and location at that moment. Coupons with no movement rows
     * (generated before the audit log existed) are absent.
     */
    // ponytail: correlated MAX per coupon — fine at today's volumes; add a daily balance snapshot table if it gets slow.
    @Query("""
            SELECT m.toStatus AS status, ft.id AS fuelTypeId, ft.name AS fuelTypeName,
                   c.denomination AS denomination, COUNT(c) AS count, SUM(c.denomination) AS litres
            FROM CouponMovement m
            JOIN m.coupon c
            JOIN c.fuelType ft
            WHERE m.id = (SELECT MAX(m2.id) FROM CouponMovement m2 WHERE m2.coupon = m.coupon AND m2.createdAt < :end)
              AND (:status IS NULL OR m.toStatus = :status)
              AND (:locationId IS NULL OR m.toLocation.id = :locationId)
              AND (:fuelTypeId IS NULL OR ft.id = :fuelTypeId)
            GROUP BY m.toStatus, ft.id, ft.name, c.denomination
            ORDER BY m.toStatus, ft.name, c.denomination
            """)
    List<CouponActivityRow> summarizeBalance(@Param("end") LocalDateTime end,
                                             @Param("status") CouponStatus status,
                                             @Param("locationId") Long locationId,
                                             @Param("fuelTypeId") Long fuelTypeId);

    /** Litres per (day, from status, to status) within [start, end) — status changes only. */
    @Query("""
            SELECT CAST(m.createdAt AS LocalDate) AS day, m.fromStatus AS fromStatus, m.toStatus AS toStatus,
                   SUM(c.denomination) AS litres
            FROM CouponMovement m
            JOIN m.coupon c
            WHERE m.movementType <> com.couponnumbergenerator.enums.MovementType.REASSIGNMENT
              AND m.createdAt >= :start AND m.createdAt < :end
              AND (:fuelTypeId IS NULL OR c.fuelType.id = :fuelTypeId)
            GROUP BY CAST(m.createdAt AS LocalDate), m.fromStatus, m.toStatus
            ORDER BY CAST(m.createdAt AS LocalDate)
            """)
    List<StatusFlowRow> summarizeDailyFlows(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("fuelTypeId") Long fuelTypeId);

    @Query("""
            SELECT a.requestedBy AS requestedBy, COUNT(c) AS count, SUM(c.denomination) AS litres
            FROM CouponMovement m
            JOIN m.coupon c
            JOIN CouponApprovalRequest a ON a.id = m.referenceId
            WHERE m.movementType = com.couponnumbergenerator.enums.MovementType.REDEMPTION
              AND m.createdAt >= :start AND m.createdAt < :end
              AND (:locationId IS NULL OR m.toLocation.id = :locationId)
              AND (:requestedBy IS NULL OR a.requestedBy = :requestedBy)
              AND (:fuelTypeId IS NULL OR c.fuelType.id = :fuelTypeId)
            GROUP BY a.requestedBy
            ORDER BY a.requestedBy
            """)
    List<RedemptionByAttendantRow> summarizeRedemptionsByAttendant(@Param("start") LocalDateTime start,
                                                                     @Param("end") LocalDateTime end,
                                                                     @Param("locationId") Long locationId,
                                                                     @Param("requestedBy") String requestedBy,
                                                                     @Param("fuelTypeId") Long fuelTypeId);
}
