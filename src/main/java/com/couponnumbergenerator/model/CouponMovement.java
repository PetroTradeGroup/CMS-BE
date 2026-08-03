package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit record of a single coupon lifecycle event.
 * Rows are insert-only — never updated or deleted (compliance requirement).
 */
@Entity
@Table(name = "coupon_movements", indexes = {
        @Index(name = "idx_movement_coupon_created", columnList = "coupon_id, created_at")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CouponMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false, updatable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 20)
    private CouponStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private CouponStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", updatable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", updatable = false)
    private Location toLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_department_id", updatable = false)
    private Department fromDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_department_id", updatable = false)
    private Department toDepartment;

    @Column(name = "performed_by", updatable = false, length = 100)
    private String performedBy;

    @Column(updatable = false, length = 255)
    private String reason;

    @Column(name = "reference_type", updatable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}