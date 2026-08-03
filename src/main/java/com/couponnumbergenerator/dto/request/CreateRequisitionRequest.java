package com.couponnumbergenerator.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRequisitionRequest(
        @NotNull(message = "Department is required")
        Long departmentId,

        @NotNull(message = "Location is required")
        Long locationId,

        @Size(max = 100, message = "Requested-by must be at most 100 characters")
        String requestedBy,

        @NotEmpty(message = "At least one line is required")
        @Valid
        List<RequisitionLineRequest> lines
) {}