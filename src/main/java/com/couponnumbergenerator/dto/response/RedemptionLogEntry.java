package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.ApprovalStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One redemption as it happened: when it was submitted (redeemedAt), who by, where (site + company), for which vehicle,
 * and what — coupon count, litres and fuel type(s). postedAt/documentNumber are set once it reached the ERP.
 */
public record RedemptionLogEntry(
        Long id,
        LocalDateTime redeemedAt,
        ApprovalStatus status,
        LocalDateTime postedAt,
        String documentNumber,
        String carRegistrationNumber,
        String attendantUsername,
        String attendantName,
        Long locationId,
        String locationCode,
        String locationName,
        String companyName,
        int couponCount,
        BigDecimal litres,
        List<String> fuelTypes
) {}
