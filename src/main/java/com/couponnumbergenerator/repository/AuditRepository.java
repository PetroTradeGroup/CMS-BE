package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.CouponMovement;
import com.couponnumbergenerator.repository.projection.AuditEventRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only, system-wide audit feed merging three different sources into one chronological list:
 * every coupon lifecycle event ({@link CouponMovement}), every decided approval request
 * (including rejections, which move no coupon and so leave no movement of their own), and every
 * security-relevant request ({@link com.couponnumbergenerator.model.SecurityAuditEvent}). Bound
 * to {@link CouponMovement} only because a Spring Data repository needs some managed entity — the
 * queries below reach across all three tables regardless.
 *
 * <p>Filtering and pagination happen in the outer query, over the aliases the UNION already
 * exposes (category/actor/occurred_at) — the source branches stay unfiltered raw feeds, so a
 * filter never has to be written three times in three different column vocabularies.
 */
public interface AuditRepository extends JpaRepository<CouponMovement, Long> {

    @Query(value = """
            SELECT category, action, actor, occurred_at AS occurredAt,
                   reference_type AS referenceType, reference_id AS referenceId, summary
            FROM (
                SELECT
                    'COUPON_LIFECYCLE' AS category,
                    cm.movement_type AS action,
                    cm.performed_by AS actor,
                    cm.created_at AS occurred_at,
                    'COUPON' AS reference_type,
                    cm.coupon_id AS reference_id,
                    CONCAT('Coupon ', c.coupon_number, ': ', COALESCE(cm.from_status, '(new)'), ' -> ', cm.to_status,
                           CASE WHEN cm.reason IS NOT NULL THEN CONCAT(' — ', cm.reason) ELSE '' END) AS summary
                FROM coupon_movements cm
                JOIN coupons c ON c.id = cm.coupon_id

                UNION ALL

                SELECT
                    'APPROVAL_DECISION' AS category,
                    car.status AS action,
                    car.decided_by AS actor,
                    car.decided_at AS occurred_at,
                    'APPROVAL_REQUEST' AS reference_type,
                    car.id AS reference_id,
                    CONCAT('Approval request #', car.id, ' (', car.request_type, ', ', car.coupon_count,
                           ' coupon(s)): ', car.status,
                           CASE WHEN car.decision_reason IS NOT NULL THEN CONCAT(' — ', car.decision_reason) ELSE '' END) AS summary
                FROM coupon_approval_requests car
                WHERE car.decided_at IS NOT NULL

                UNION ALL

                SELECT
                    'SECURITY' AS category,
                    sae.outcome AS action,
                    sae.principal AS actor,
                    sae.created_at AS occurred_at,
                    'SECURITY_EVENT' AS reference_type,
                    sae.id AS reference_id,
                    CONCAT(sae.http_method, ' ', sae.path, ' -> ', sae.status_code,
                           CASE WHEN sae.roles IS NOT NULL THEN CONCAT(' (', sae.roles, ')') ELSE '' END,
                           CASE WHEN sae.reason IS NOT NULL THEN CONCAT(' — ', sae.reason) ELSE '' END) AS summary
                FROM security_audit_log sae
            ) audit_feed
            WHERE (:category IS NULL OR category = :category)
              AND (:actor IS NULL OR actor ILIKE CONCAT('%', CAST(:actor AS text), '%'))
              AND (CAST(:dateFrom AS timestamp) IS NULL OR occurred_at >= CAST(:dateFrom AS timestamp))
              AND (CAST(:dateTo AS timestamp) IS NULL OR occurred_at < CAST(:dateTo AS timestamp))
            ORDER BY occurred_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<AuditEventRow> findAuditEvents(@Param("category") String category,
                                        @Param("actor") String actor,
                                        @Param("dateFrom") LocalDateTime dateFrom,
                                        @Param("dateTo") LocalDateTime dateTo,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);

    /** Same filtered feed as {@link #findAuditEvents}, unpaged — backs the Excel/PDF export, which (like batch and redemption exports) dumps the whole filtered set in one go. */
    @Query(value = """
            SELECT category, action, actor, occurred_at AS occurredAt,
                   reference_type AS referenceType, reference_id AS referenceId, summary
            FROM (
                SELECT
                    'COUPON_LIFECYCLE' AS category,
                    cm.movement_type AS action,
                    cm.performed_by AS actor,
                    cm.created_at AS occurred_at,
                    'COUPON' AS reference_type,
                    cm.coupon_id AS reference_id,
                    CONCAT('Coupon ', c.coupon_number, ': ', COALESCE(cm.from_status, '(new)'), ' -> ', cm.to_status,
                           CASE WHEN cm.reason IS NOT NULL THEN CONCAT(' — ', cm.reason) ELSE '' END) AS summary
                FROM coupon_movements cm
                JOIN coupons c ON c.id = cm.coupon_id

                UNION ALL

                SELECT
                    'APPROVAL_DECISION' AS category,
                    car.status AS action,
                    car.decided_by AS actor,
                    car.decided_at AS occurred_at,
                    'APPROVAL_REQUEST' AS reference_type,
                    car.id AS reference_id,
                    CONCAT('Approval request #', car.id, ' (', car.request_type, ', ', car.coupon_count,
                           ' coupon(s)): ', car.status,
                           CASE WHEN car.decision_reason IS NOT NULL THEN CONCAT(' — ', car.decision_reason) ELSE '' END) AS summary
                FROM coupon_approval_requests car
                WHERE car.decided_at IS NOT NULL

                UNION ALL

                SELECT
                    'SECURITY' AS category,
                    sae.outcome AS action,
                    sae.principal AS actor,
                    sae.created_at AS occurred_at,
                    'SECURITY_EVENT' AS reference_type,
                    sae.id AS reference_id,
                    CONCAT(sae.http_method, ' ', sae.path, ' -> ', sae.status_code,
                           CASE WHEN sae.roles IS NOT NULL THEN CONCAT(' (', sae.roles, ')') ELSE '' END,
                           CASE WHEN sae.reason IS NOT NULL THEN CONCAT(' — ', sae.reason) ELSE '' END) AS summary
                FROM security_audit_log sae
            ) audit_feed
            WHERE (:category IS NULL OR category = :category)
              AND (:actor IS NULL OR actor ILIKE CONCAT('%', CAST(:actor AS text), '%'))
              AND (CAST(:dateFrom AS timestamp) IS NULL OR occurred_at >= CAST(:dateFrom AS timestamp))
              AND (CAST(:dateTo AS timestamp) IS NULL OR occurred_at < CAST(:dateTo AS timestamp))
            ORDER BY occurred_at DESC
            """, nativeQuery = true)
    List<AuditEventRow> findAllAuditEvents(@Param("category") String category,
                                           @Param("actor") String actor,
                                           @Param("dateFrom") LocalDateTime dateFrom,
                                           @Param("dateTo") LocalDateTime dateTo);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 'COUPON_LIFECYCLE' AS category, cm.performed_by AS actor, cm.created_at AS occurred_at
                FROM coupon_movements cm

                UNION ALL

                SELECT 'APPROVAL_DECISION' AS category, car.decided_by AS actor, car.decided_at AS occurred_at
                FROM coupon_approval_requests car
                WHERE car.decided_at IS NOT NULL

                UNION ALL

                SELECT 'SECURITY' AS category, sae.principal AS actor, sae.created_at AS occurred_at
                FROM security_audit_log sae
            ) audit_feed
            WHERE (:category IS NULL OR category = :category)
              AND (:actor IS NULL OR actor ILIKE CONCAT('%', CAST(:actor AS text), '%'))
              AND (CAST(:dateFrom AS timestamp) IS NULL OR occurred_at >= CAST(:dateFrom AS timestamp))
              AND (CAST(:dateTo AS timestamp) IS NULL OR occurred_at < CAST(:dateTo AS timestamp))
            """, nativeQuery = true)
    long countAuditEvents(@Param("category") String category,
                          @Param("actor") String actor,
                          @Param("dateFrom") LocalDateTime dateFrom,
                          @Param("dateTo") LocalDateTime dateTo);
}
