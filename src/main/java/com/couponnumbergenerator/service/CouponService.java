package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.dto.request.GenerateBulkCouponRequest;
import com.couponnumbergenerator.dto.request.GenerateCouponRequest;
import com.couponnumbergenerator.dto.request.ImportLegacyCouponRequest;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface CouponService {

    CouponResponse generateCoupon(GenerateCouponRequest request);

    List<CouponResponse> generateBulkCoupons(GenerateBulkCouponRequest request);

    /**
     * Registers a coupon that predates this system (e.g. an old barcoded coupon) directly at
     * ALLOCATED with the given couponNumber, so it can be redeemed through the normal
     * {@code /redemptions} flow. Fails if couponNumber is already in use.
     */
    CouponResponse importLegacyCoupon(ImportLegacyCouponRequest request);

    CouponResponse getCouponByNumber(String couponNumber);

    PagedResponse<CouponResponse> getCoupons(CouponFilterRequest filter, Pageable pageable);

    PagedResponse<CouponResponse> getCouponsByFuelType(Long fuelTypeId, Pageable pageable);

    void exportCoupons(CouponFilterRequest filter, OutputStream outputStream) throws IOException;
}