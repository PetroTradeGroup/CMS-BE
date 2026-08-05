package com.couponnumbergenerator.event;

/** Published after a redemption request is committed as PENDING, to trigger the ERP auto-post. */
public record RedemptionSubmittedEvent(Long approvalRequestId) {}