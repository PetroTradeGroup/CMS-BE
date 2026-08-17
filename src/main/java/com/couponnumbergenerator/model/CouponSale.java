package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.SaleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A coupon sale received from Business Central (Option A — "coupon-blind ERP", see
 * {@code docs/erp-sales-integration-design.md}): BC posts the sale as a financial document
 * with no knowledge of serials; the CMS assigns serials from its own stock, marks them
 * ALLOCATED, and confirms the assigned range back to BC. Unlike a
 * {@link CouponApprovalRequest}, this isn't gated by supervisor approval — the money's
 * already been collected in BC — so it's an inbound audit record, not a workflow item.
 */
@Entity
@Table(name = "coupon_sales", indexes = {
        @Index(name = "idx_coupon_sale_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** BC's sale document number — the idempotency key for the inbound webhook. */
    @Column(name = "bc_document_number", unique = true, nullable = false, length = 50)
    private String bcDocumentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal denomination;

    /** How many coupons BC's sale asked for. */
    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    /** The serials assigned against this sale, in the order they were drawn. Empty until ASSIGNED. */
    @ElementCollection
    @CollectionTable(name = "coupon_sale_coupon_numbers", joinColumns = @JoinColumn(name = "coupon_sale_id"))
    @Column(name = "coupon_number", length = 20)
    @Builder.Default
    private List<String> couponNumbers = new ArrayList<>();

    /** Whatever BC sends to identify the customer; opaque to the CMS. */
    @Column(name = "customer_reference", length = 100)
    private String customerReference;

    /** Why assignment failed (e.g. insufficient stock). Set only when FAILED. */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** When the assigned range was confirmed back to BC. */
    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}