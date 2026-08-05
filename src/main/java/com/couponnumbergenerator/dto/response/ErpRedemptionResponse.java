package com.couponnumbergenerator.dto.response;

/** What the ERP's redemption endpoint returns: the posted document's number. */
public record ErpRedemptionResponse(String documentNumber) {}