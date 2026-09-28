package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ErpSaleConfirmationRequest;
import com.couponnumbergenerator.enums.SaleLineStatus;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.model.CouponSale;
import com.couponnumbergenerator.repository.CouponSaleRepository;
import com.couponnumbergenerator.service.SaleErpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Confirms one sale's assigned serial ranges back to BC — one entry per ASSIGNED line — and
 * marks the sale PUSHED. If the BC call fails the transaction ends with nothing changed and
 * the sale stays ASSIGNED / PARTIALLY_ASSIGNED, so {@link CouponSaleSync}'s retry sweep can
 * finish the job later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSalePusher {

    /** A sale in one of these states still has assigned lines that BC hasn't confirmed. */
    static final Set<SaleStatus> PENDING_PUSH = EnumSet.of(SaleStatus.ASSIGNED, SaleStatus.PARTIALLY_ASSIGNED);

    private final CouponSaleRepository couponSaleRepository;
    private final SaleErpClient saleErpClient;

    @Transactional
    public void pushToErp(Long saleId) {
        CouponSale sale = couponSaleRepository.findById(saleId).orElse(null);
        if (sale == null || !PENDING_PUSH.contains(sale.getStatus())) {
            return;
        }

        List<ErpSaleConfirmationRequest.Line> lines = sale.getLines().stream()
                .filter(line -> line.getStatus() == SaleLineStatus.ASSIGNED)
                .map(line -> new ErpSaleConfirmationRequest.Line(
                        line.getLineNumber(),
                        line.getFuelType().getId(),
                        line.getDenomination(),
                        line.getCouponNumbers()))
                .toList();

        saleErpClient.confirmSale(new ErpSaleConfirmationRequest(sale.getBcDocumentNumber(), lines));
        sale.setStatus(SaleStatus.PUSHED);
        sale.setPushedAt(LocalDateTime.now());
    }
}
