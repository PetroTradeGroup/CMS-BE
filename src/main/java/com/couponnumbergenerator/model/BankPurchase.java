package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.BankPurchaseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Virtual coupons bought by a customer through a partner bank. The bank collects the money and
 * settles with Petrotrade offline; this row is our side of that reconciliation. The purchase's
 * coupons are exactly the DIGITAL coupons of {@link #batch}, minted on demand for it.
 */
@Entity
@Table(name = "bank_purchases", uniqueConstraints = {
        @UniqueConstraint(name = "uq_bank_purchases_bank_reference", columnNames = {"bank_code", "bank_reference"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which bank made the purchase: its Keycloak client id (the token's azp claim). */
    @Column(name = "bank_code", nullable = false, length = 50)
    private String bankCode;

    /** The bank's transaction reference — with bankCode, the idempotency key for purchase retries. */
    @Column(name = "bank_reference", nullable = false, length = 50)
    private String bankReference;

    /** Whatever the bank sends to identify its customer; opaque to the CMS. */
    @Column(name = "customer_reference", length = 100)
    private String customerReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private CouponBatch batch;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal litres;

    @Column(name = "price_per_litre", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerLitre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankPurchaseStatus status;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
