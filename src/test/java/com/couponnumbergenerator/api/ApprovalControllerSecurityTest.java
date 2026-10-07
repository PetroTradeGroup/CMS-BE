package com.couponnumbergenerator.api;

import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only a Stocks Controller signs off an issue raised against a requisition. */
@WebMvcTest(controllers = ApprovalController.class)
@Import(SecurityConfig.class)
class ApprovalControllerSecurityTest {

    private static final String BODY = """
            {"approvedBy": "someone"}""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponLifecycleService couponLifecycleService;
    @MockitoBean private SecurityAuditService securityAuditService;

    private void givenApproval(Long requisitionId) {
        ApprovalRequestResponse approval = mock(ApprovalRequestResponse.class);
        when(approval.requisitionId()).thenReturn(requisitionId);
        when(couponLifecycleService.getApprovalRequest(1L)).thenReturn(approval);
    }

    @Test
    void onlyStocksControllerApprovesARequisitionIssue() throws Exception {
        givenApproval(5L);
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_REGIONAL_REP", "ROLE_STOCKS_CLERK"}) {
            mockMvc.perform(post("/api/v1/approvals/1/approve").with(jwt().authorities(() -> role))
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/approvals/1/approve").with(jwt().authorities(() -> "ROLE_STOCKS_CONTROLLER"))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void onlyStocksControllerRejectsARequisitionIssue() throws Exception {
        givenApproval(5L);
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_REGIONAL_REP"}) {
            mockMvc.perform(post("/api/v1/approvals/1/reject").with(jwt().authorities(() -> role))
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/approvals/1/reject").with(jwt().authorities(() -> "ROLE_STOCKS_CONTROLLER"))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void onlyCommercialManagerConfirmsReceiptOfARequisitionIssue() throws Exception {
        givenApproval(5L);
        String body = """
                {"receivedBy": "someone"}""";
        for (String role : new String[]{"ROLE_STOCKS_CONTROLLER", "ROLE_REGIONAL_REP", "ROLE_ADMIN"}) {
            mockMvc.perform(post("/api/v1/approvals/1/confirm-receipt").with(jwt().authorities(() -> role))
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/approvals/1/confirm-receipt").with(jwt().authorities(() -> "ROLE_COMMERCIAL_MANAGER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    void otherApprovalsStillOpenToAdminAndRep() throws Exception {
        givenApproval(null);
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_REGIONAL_REP"}) {
            mockMvc.perform(post("/api/v1/approvals/1/approve").with(jwt().authorities(() -> role))
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isOk());
        }
    }
}
