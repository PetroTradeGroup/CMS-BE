package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.SaleLineStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One line of a {@link CouponSale} — a single fuel type + denomination + quantity within a BC
 * sales document. A document with two denominations arrives as one {@link CouponSale} with two
 * of these (see §11.6 of {@code docs/erp-sales-integration-design.md}). Each line is assigned or
 * fails on stock independently; the parent sale's status is the rollup.
 */
@Entity
@Table(name = "coupon_sale_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uq_coupon_sale_line", columnNames = {"coupon_sale_id", "line_number"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponSaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_sale_id", nullable = false)
    private CouponSale sale;

    /** BC's line number if it sends one, otherwise the 1-based ordinal within the document. */
    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal denomination;

    /** How many coupons this line asked for. */
    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    /**
     * BC's signal that this line is sold as whole intact books rather than a loose count.
     * Stored now; the whole-book allocation path itself is a later build item (§11.4).
     */
    @Column(name = "whole_books", nullable = false)
    @Builder.Default
    private boolean wholeBooks = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleLineStatus status;

    /** Why this line failed (e.g. the stock shortfall). Set only when FAILED. */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** The serials assigned against this line, in the order they were drawn. Empty until ASSIGNED. */
    @ElementCollection
    @CollectionTable(name = "coupon_sale_line_coupon_numbers",
            joinColumns = @JoinColumn(name = "coupon_sale_line_id"))
    @Column(name = "coupon_number", length = 20)
    @Builder.Default
    private List<String> couponNumbers = new ArrayList<>();
}
