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

    Optional<Coupon> findByRedemptionCode(String redemptionCode);

    List<Coupon> findByRedemptionCodeIn(Collection<String> redemptionCodes);

    /**
     * Locks the given coupons {@code FOR UPDATE}, in coupon-number order so two callers locking
     * overlapping sets can't deadlock. Redemption submission takes this lock before its
     * "already pending?" check, so two stations submitting the same coupon at once serialise:
     * the second waits, then sees the first's pending request and is refused.
     */
    @Query(value = "SELECT * FROM coupons WHERE coupon_number IN (:numbers) ORDER BY coupon_number FOR UPDATE",
            nativeQuery = true)
    List<Coupon> lockByCouponNumberIn(@Param("numbers") Collection<String> numbers);

    long countByStatus(CouponStatus status);

    long countByFuelTypeId(Long fuelTypeId);

    long countByFuelTypeIdAndStatus(Long fuelTypeId, CouponStatus status);

    @Query("SELECT COUNT(c) FROM Coupon c WHERE c.createdAt >= :from AND c.createdAt < :to")
    long countByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * How many coupons of a batch + denomination Stock could issue right now: IN_STOCK and still
     * sitting in the stock department — the same eligibility a denomination-pick transfer applies.
     */
    @Query("""
            SELECT COUNT(c) FROM Coupon c
            WHERE c.batch.id = :batchId
              AND c.denomination = :denomination
              AND c.status = com.couponnumbergenerator.enums.CouponStatus.IN_STOCK
              AND c.currentDepartment.code = :departmentCode
            """)
    long countIssuable(@Param("batchId") Long batchId,
                       @Param("denomination") BigDecimal denomination,
                       @Param("departmentCode") String departmentCode);

    /**
     * Up to {@code limit} coupons eligible to sell at {@code locationId} — IN_STOCK, in the
     * stock department, matching fuel type + denomination — in the strict order stock must be
     * drawn: oldest <b>batch sequence number</b> first, then one <b>book</b> at a time, then
     * position within the book (§11.2 of {@code docs/erp-sales-integration-design.md}).
     *
     * <p>Locks the returned coupon rows {@code FOR UPDATE} (blocking, no {@code SKIP LOCKED}):
     * a second concurrent sale at the same site waits for this one to commit, then re-reads and
     * sees the reduced stock — so two sales can never assign the same serials, and neither sees
     * a false shortage. Only the coupon rows are locked, not the joined batch. Native because
     * JPQL {@code @Lock} can't scope {@code FOR UPDATE} to one table. Must run inside the
     * assignment transaction (which is read-write — {@code SELECT FOR UPDATE} is rejected in a
     * read-only transaction).
     */
    @Query(value = """
            SELECT c.* FROM coupons c
            JOIN coupon_batches b ON b.id = c.batch_id
            WHERE c.current_location_id = :locationId
              AND c.fuel_type_id = :fuelTypeId
              AND c.denomination = :denomination
              AND c.status = 'IN_STOCK'
              AND c.current_department_id = (SELECT d.id FROM departments d WHERE d.code = :departmentCode)
            ORDER BY b.sequence_number ASC, c.book_number ASC NULLS LAST, c.batch_sequence ASC
            LIMIT :limit
            FOR UPDATE OF c
            """, nativeQuery = true)
    List<Coupon> findIssuableForSale(@Param("locationId") Long locationId,
                                     @Param("fuelTypeId") Long fuelTypeId,
                                     @Param("denomination") BigDecimal denomination,
                                     @Param("departmentCode") String departmentCode,
                                     @Param("limit") int limit);

    /**
     * The <b>whole</b> eligible IN_STOCK pool at {@code locationId} (fuel type + denomination,
     * stock department) — no {@code LIMIT} — locked {@code FOR UPDATE} and returned in the same
     * selling order as {@link #findIssuableForSale}. For whole-book assignment (§11.4 of
     * {@code docs/erp-sales-integration-design.md}): the caller buckets these rows into books
     * ({@code batch.sequence_number} + {@code book_number}) in application code and takes the
     * first N that are still 100/100 IN_STOCK.
     *
     * <p>Grouping in the DB with {@code HAVING count(*) = BOOK_SIZE} can't be combined safely
     * with a blocking {@code FOR UPDATE}: Postgres re-checks each locked row against the WHERE
     * after a concurrent commit, but does not re-run the aggregate, so a second concurrent sale
     * could pick a book from a stale snapshot and then see a false shortage. Locking the flat
     * pool and grouping afterwards avoids that — a concurrent sale blocks here, then re-reads
     * only rows still IN_STOCK. Whole-book sales are rare and BC posts sale events
     * single-threaded, so the wider lock scope is acceptable. Must run in the read-write
     * assignment transaction.
     */
    @Query(value = """
            SELECT c.* FROM coupons c
            JOIN coupon_batches b ON b.id = c.batch_id
            WHERE c.current_location_id = :locationId
              AND c.fuel_type_id = :fuelTypeId
              AND c.denomination = :denomination
              AND c.status = 'IN_STOCK'
              AND c.current_department_id = (SELECT d.id FROM departments d WHERE d.code = :departmentCode)
            ORDER BY b.sequence_number ASC, c.book_number ASC NULLS LAST, c.batch_sequence ASC
            FOR UPDATE OF c
            """, nativeQuery = true)
    List<Coupon> lockIssuablePoolForSale(@Param("locationId") Long locationId,
                                         @Param("fuelTypeId") Long fuelTypeId,
                                         @Param("denomination") BigDecimal denomination,
                                         @Param("departmentCode") String departmentCode);

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