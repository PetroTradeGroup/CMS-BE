package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateFuelTypeRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 20, message = "Name must be at most 20 characters")
        String name,

        @NotBlank(message = "Type code is required")
        @Size(min = 3, max = 5, message = "Type code must be 3–5 characters")
        String typeCode,

        String description,

        /** Bank-channel price per litre; null keeps this fuel type off the bank catalog. */
        @Positive(message = "Price per litre must be greater than zero")
        BigDecimal pricePerLitre
) {}
