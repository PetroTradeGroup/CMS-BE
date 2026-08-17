package com.couponnumbergenerator.dto.response;

import java.util.List;

/**
 * What an auto-fulfill planned: one deferred transfer (supervisor approval pending) per batch
 * drawn from, plus the requisition's state afterwards — litres count as fulfilled only once
 * each transfer is approved and received, so outstanding balances here still reflect that.
 */
public record AutoFulfillResponse(
        RequisitionResponse requisition,
        List<ApprovalRequestResponse> transfers
) {}