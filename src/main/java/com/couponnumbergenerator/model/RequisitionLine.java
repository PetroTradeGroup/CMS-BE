package com.couponnumbergenerator.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** One denomination line of a {@link CouponRequisition} — litres requested vs. litres delivered so far. */
@Entity
@Table(name = "requisition_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisitionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisition_id", nullable = false)
    private CouponRequisition requisition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal denomination;

    @Column(name = "requested_litres", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedLitres;

    @Column(name = "fulfilled_litres", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal fulfilledLitres = BigDecimal.ZERO;

    public BigDecimal outstandingLitres() {
        return requestedLitres.subtract(fulfilledLitres);
    }

    public boolean isFullyFulfilled() {
        return fulfilledLitres.compareTo(requestedLitres) >= 0;
    }
}