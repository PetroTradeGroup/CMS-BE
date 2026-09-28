package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.BankPurchaseStatus;
import com.couponnumbergenerator.enums.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BankPurchaseResponse(
        String bankCode,
        String bankReference,
        String customerReference,
        BankPurchaseStatus status,
        String fuelType,
        BigDecimal litres,
        BigDecimal pricePerLitre,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt,
        LocalDateTime reversedAt,
        String reversalReason,
        List<VirtualCoupon> coupons
) {
    /**
     * What the bank shows the customer: {@code qrPayload} rendered as a QR code for the station to
     * scan, and {@code redemptionCode} for the customer to read out when scanning isn't possible.
     * Both are bearer secrets — whoever holds either can spend the coupon.
     */
    public record VirtualCoupon(
            String couponNumber,
            String redemptionCode,
            BigDecimal denomination,
            CouponStatus status,
            LocalDate expiryDate,
            String qrPayload
    ) {}

    /** What {@code requestedAmount} buys in whole litres — one or two options to offer the customer. */
    public record Quote(
            Long fuelTypeId,
            String fuelType,
            BigDecimal pricePerLitre,
            String currency,
            BigDecimal requestedAmount,
            List<QuoteOption> options
    ) {}

    /** Send {@code amount} to POST /bank/purchases/by-amount to buy exactly {@code litres}. */
    public record QuoteOption(long litres, BigDecimal amount) {}

    /** A fuel type the bank may sell, at its current price. */
    public record CatalogItem(
            Long fuelTypeId,
            String name,
            String typeCode,
            BigDecimal pricePerLitre,
            String currency
    ) {}
}
