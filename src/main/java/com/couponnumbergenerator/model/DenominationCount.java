package com.couponnumbergenerator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** How many coupons of one denomination a {@link CouponApprovalRequest} covers. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DenominationCount {

    @Column(name = "denomination", precision = 10, scale = 2)
    private BigDecimal denomination;

    @Column(name = "count")
    private int count;
}