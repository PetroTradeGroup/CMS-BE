package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ErpSaleConfirmationRequest;
import com.couponnumbergenerator.service.SaleErpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stand-in for Business Central's confirmation endpoint while it doesn't exist yet
 * ({@code app.erp.sales.mock=true}): logs and does nothing further. Swap to
 * {@link RestSaleErpClient} by setting {@code app.erp.sales.mock=false} once BC exposes
 * the real endpoint and an API key is available.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.erp.sales", name = "mock", havingValue = "true")
public class MockSaleErpClient implements SaleErpClient {

    @Override
    public void confirmSale(ErpSaleConfirmationRequest request) {
        log.info("MOCK BC: sale {} confirmed with {} coupon(s)",
                request.documentNumber(), request.couponNumbers().size());
    }
}