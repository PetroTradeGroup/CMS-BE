package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create (code required) or update (code ignored — it's the stable identifier) a company. active defaults to true. */
public record CompanyRequest(
        @Size(max = 20, message = "Code must be at most 20 characters")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        Boolean active
) {}
