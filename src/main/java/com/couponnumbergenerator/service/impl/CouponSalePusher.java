package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ErpSaleConfirmationRequest;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.model.CouponSale;
import com.couponnumbergenerator.repository.CouponSaleRepository;
import com.couponnumbergenerator.service.SaleErpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Confirms one ASSIGNED sale's serial range back to BC and marks it PUSHED. If the BC call
 * fails the transaction ends with nothing changed and the sale stays ASSIGNED, so
 * {@link CouponSaleSync}'s retry sweep can finish the job later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSalePusher {

    private final CouponSaleRepository couponSaleRepository;
    private final SaleErpClient saleErpClient;

    @Transactional
    public void pushToErp(Long saleId) {
        CouponSale sale = couponSaleRepository.findById(saleId).orElse(null);
        if (sale == null || sale.getStatus() != SaleStatus.ASSIGNED) {
            return;
        }
        saleErpClient.confirmSale(new ErpSaleConfirmationRequest(sale.getBcDocumentNumber(), sale.getCouponNumbers()));
        sale.setStatus(SaleStatus.PUSHED);
        sale.setPushedAt(LocalDateTime.now());
    }
}