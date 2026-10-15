package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignCompanyRequest(@NotNull(message = "companyId is required") Long companyId) {}
