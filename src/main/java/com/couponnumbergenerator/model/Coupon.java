package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons", indexes = {
        @Index(name = "idx_coupon_fuel_type_id", columnList = "fuel_type_id"),
        @Index(name = "idx_coupon_status", columnList = "status"),
        @Index(name = "idx_coupon_created_at", columnList = "created_at"),
        @Index(name = "idx_coupon_batch_id", columnList = "batch_id"),
        @Index(name = "idx_coupon_current_location_id", columnList = "current_location_id"),
        @Index(name = "idx_coupon_location_status", columnList = "current_location_id, status"),
        @Index(name = "idx_coupon_current_department_id", columnList = "current_department_id"),
        @Index(name = "idx_coupon_batch_sequence", columnList = "batch_id, batch_sequence")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_number", unique = true, nullable = false, length = 20)
    private String couponNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponStatus status;

    /** Fuel volume (litres) this specific coupon is redeemable for. */
    @Column(name = "denomination", nullable = false, precision = 10, scale = 2)
    private BigDecimal denomination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private CouponBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_location_id", nullable = false)
    private Location currentLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_department_id", nullable = false)
    private Department currentDepartment;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 10)
    @Builder.Default
    private CouponType couponType = CouponType.PHYSICAL;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Position of this coupon within its batch (1-indexed, generation order). Null for coupons generated before this field existed. */
    @Column(name = "batch_sequence")
    private Integer batchSequence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = CouponStatus.GENERATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}