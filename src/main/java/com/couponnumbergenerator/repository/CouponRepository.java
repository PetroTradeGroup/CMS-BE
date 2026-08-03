package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.repository.projection.InventorySummaryRow;
import com.couponnumbergenerator.repository.projection.StatusCountRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long>, JpaSpecificationExecutor<Coupon> {

    Optional<Coupon> findByCouponNumber(String couponNumber);

    List<Coupon> findByCouponNumberIn(Collection<String> couponNumbers);

    Page<Coupon> findByFuelTypeId(Long fuelTypeId, Pageable pageable);

    List<Coupon> findByStatus(CouponStatus status);

    List<Coupon> findByBatchIdAndStatus(Long batchId, CouponStatus status);

    List<Coupon> findByBatchIdOrderByBatchSequenceAsc(Long batchId);

    List<Coupon> findByBatchIdAndBatchSequenceBetweenOrderByBatchSequenceAsc(Long batchId, Integer from, Integer to);

    List<Coupon> findByBatchIdAndDenominationOrderByBatchSequenceAsc(Long batchId, BigDecimal denomination);

    boolean existsByCouponNumber(String couponNumber);

    long countByStatus(CouponStatus status);

    long countByFuelTypeId(Long fuelTypeId);

    long countByFuelTypeIdAndStatus(Long fuelTypeId, CouponStatus status);

    @Query("SELECT COUNT(c) FROM Coupon c WHERE c.createdAt >= :from AND c.createdAt < :to")
    long countByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT l.id AS locationId, l.code AS locationCode, l.name AS locationName,
                   ft.id AS fuelTypeId, ft.name AS fuelTypeName,
                   c.status AS status, COUNT(c) AS count
            FROM Coupon c
            JOIN c.currentLocation l
            JOIN c.fuelType ft
            WHERE (:locationId IS NULL OR l.id = :locationId)
              AND (:fuelTypeId IS NULL OR ft.id = :fuelTypeId)
              AND (:status IS NULL OR c.status = :status)
            GROUP BY l.id, l.code, l.name, ft.id, ft.name, c.status
            ORDER BY l.name, ft.name, c.status
            """)
    List<InventorySummaryRow> summarizeInventory(@Param("locationId") Long locationId,
                                                 @Param("fuelTypeId") Long fuelTypeId,
                                                 @Param("status") CouponStatus status);

    @Query("SELECT c.status AS status, COUNT(c) AS count FROM Coupon c WHERE c.batch.id = :batchId GROUP BY c.status")
    List<StatusCountRow> countByBatchGroupedByStatus(@Param("batchId") Long batchId);

    @Query("SELECT c.status AS status, COUNT(c) AS count FROM Coupon c GROUP BY c.status")
    List<StatusCountRow> countGroupedByStatus();

    @Query("SELECT c.status AS status, COUNT(c) AS count FROM Coupon c WHERE c.fuelType.id = :fuelTypeId GROUP BY c.status")
    List<StatusCountRow> countByFuelTypeGroupedByStatus(@Param("fuelTypeId") Long fuelTypeId);
}