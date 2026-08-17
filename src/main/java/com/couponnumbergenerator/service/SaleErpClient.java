package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.ErpSaleConfirmationRequest;

/** Outbound calls to the ERP (Business Central) confirming an assigned coupon sale. */
public interface SaleErpClient {

    /**
     * Confirms an assigned serial range back to BC. Throws
     * {@link com.couponnumbergenerator.exception.ErpIntegrationException} if BC is
     * unreachable or rejects the confirmation.
     */
    void confirmSale(ErpSaleConfirmationRequest request);
}