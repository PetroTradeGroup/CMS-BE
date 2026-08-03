package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** One line of a requisition or a fulfillment against one: a litre quantity of a given fuel type at a given coupon denomination. */
public record RequisitionLineRequest(
        @NotNull(message = "Fuel type is required")
        Long fuelTypeId,

        @NotNull(message = "Denomination is required")
        @Positive(message = "Denomination must be greater than zero")
        BigDecimal denomination,

        @NotNull(message = "Litres is required")
        @Positive(message = "Litres must be greater than zero")
        BigDecimal litres
) {}