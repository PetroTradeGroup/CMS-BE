package com.couponnumbergenerator.dto.request;

import java.util.List;

/** Pushed back to BC once serials are assigned: its own document number, plus the assigned range. */
public record ErpSaleConfirmationRequest(
        String documentNumber,
        List<String> couponNumbers
) {}