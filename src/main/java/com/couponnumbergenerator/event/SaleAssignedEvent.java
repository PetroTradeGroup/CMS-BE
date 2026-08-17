package com.couponnumbergenerator.event;

/** Published after a {@link com.couponnumbergenerator.model.CouponSale} is committed as ASSIGNED, to trigger pushing the assigned range back to BC. */
public record SaleAssignedEvent(Long saleId) {}