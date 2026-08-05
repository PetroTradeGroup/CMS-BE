package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.ErpRedemptionRequest;

/** Outbound calls to the ERP (Navision). */
public interface ErpClient {

    /**
     * Posts a redemption to the ERP's redemption endpoint and returns the document number
     * the ERP created for it. Throws
     * {@link com.couponnumbergenerator.exception.ErpIntegrationException} if the ERP is
     * unreachable or answers without a document number.
     */
    String postRedemption(ErpRedemptionRequest request);
}