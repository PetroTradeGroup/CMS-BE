package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.CouponType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_batches", indexes = {
        @Index(name = "idx_batch_fuel_type_id", columnList = "fuel_type_id"),
        @Index(name = "idx_batch_origin_location_id", columnList = "origin_location_id"),
        @Index(name = "idx_batch_created_at", columnList = "created_at"),
        @Index(name = "idx_batch_fuel_type_sequence", columnList = "fuel_type_id, sequence_number", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_number", unique = true, nullable = false, length = 30)
    private String batchNumber;

    /**
     * 1-based, monotonic per fuel type (batch 1, 2, 3… of PETROL, tracked separately from
     * DIESEL). This is the canonical order coupon stock is sold in — oldest sequence number
     * first — and the number Stocks and auditors quote for a batch. Assigned from the fuel
     * type's {@link CouponSequence} counter at creation. See §11.3 of
     * docs/erp-sales-integration-design.md.
     */
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 10)
    private CouponType couponType;

    @Column(nullable = false)
    private int quantity;

    /** Fuel volume (litres) originally requested; must equal the sum of this batch's coupon denominations. */
    @Column(name = "target_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_location_id", nullable = false)
    private Location originLocation;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}