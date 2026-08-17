package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.ErpSaleRequest;
import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.event.SaleAssignedEvent;
import com.couponnumbergenerator.exception.CouponSaleNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponSale;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.CouponSaleRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 4 ERP sales integration (Option A — "coupon-blind ERP"): resolves an inbound BC sale
 * event into CMS serials. See {@code docs/erp-sales-integration-design.md} for the full design.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSaleServiceImpl implements CouponSaleService {

    /** Recorded as performedBy on coupons allocated via an ERP sale — there's no human actor. */
    private static final String ERP_SALE_ACTOR = "ERP-SALE";

    private final CouponSaleRepository couponSaleRepository;
    private final CouponRepository couponRepository;
    private final LocationRepository locationRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final CouponLifecycleService couponLifecycleService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CouponSaleResponse receiveSale(ErpSaleRequest request) {
        CouponSale existing = couponSaleRepository.findByBcDocumentNumber(request.documentNumber()).orElse(null);
        if (existing != null) {
            log.info("Sale event for BC document {} already received (sale #{}) — no-op",
                    request.documentNumber(), existing.getId());
            return CouponSaleResponse.from(existing);
        }

        Location location = resolveLocation(request.locationCode());
        FuelType fuelType = resolveFuelType(request.fuelTypeId());
        List<Coupon> issuable = couponRepository.findIssuableForSale(location.getId(), fuelType.getId(),
                request.denomination(), CouponConstants.DEFAULT_DEPARTMENT_CODE, PageRequest.of(0, request.quantity()));

        CouponSale sale = CouponSale.builder()
                .bcDocumentNumber(request.documentNumber())
                .location(location)
                .fuelType(fuelType)
                .denomination(request.denomination())
                .requestedCount(request.quantity())
                .customerReference(request.customerReference())
                .build();

        if (issuable.size() < request.quantity()) {
            sale.setStatus(SaleStatus.FAILED);
            sale.setFailureReason("Only %d of %d requested %s coupon(s) available at %s".formatted(
                    issuable.size(), request.quantity(), request.denomination().toPlainString(), location.getCode()));
            CouponSale saved = couponSaleRepository.save(sale);
            log.warn("Sale {} FAILED: {}", saved.getBcDocumentNumber(), saved.getFailureReason());
            return CouponSaleResponse.from(saved);
        }

        sale.setCouponNumbers(issuable.stream().map(Coupon::getCouponNumber).toList());
        sale.setStatus(SaleStatus.ASSIGNED);
        sale.setAssignedAt(LocalDateTime.now());
        CouponSale saved = couponSaleRepository.save(sale);

        couponLifecycleService.allocateForSale(issuable, ERP_SALE_ACTOR, saved.getId());

        log.info("Sale {} ASSIGNED {} coupon(s) at {} (sale #{})",
                saved.getBcDocumentNumber(), issuable.size(), location.getCode(), saved.getId());
        eventPublisher.publishEvent(new SaleAssignedEvent(saved.getId()));
        return CouponSaleResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponSaleResponse getByDocumentNumber(String bcDocumentNumber) {
        return couponSaleRepository.findByBcDocumentNumber(bcDocumentNumber)
                .map(CouponSaleResponse::from)
                .orElseThrow(() -> new CouponSaleNotFoundException(bcDocumentNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CouponSaleResponse> getSales(SaleStatus status, Pageable pageable) {
        return PagedResponse.from((status == null
                ? couponSaleRepository.findAll(pageable)
                : couponSaleRepository.findByStatus(status, pageable))
                .map(CouponSaleResponse::from));
    }

    private Location resolveLocation(String locationCode) {
        return locationRepository.findByCode(locationCode)
                .orElseThrow(() -> new LocationNotFoundException(
                        "Unknown location code from BC: '%s' — check Location.code is in sync with Business Central"
                                .formatted(locationCode)));
    }

    private FuelType resolveFuelType(Long fuelTypeId) {
        return fuelTypeRepository.findById(fuelTypeId)
                .orElseThrow(() -> new FuelTypeNotFoundException(fuelTypeId));
    }
}