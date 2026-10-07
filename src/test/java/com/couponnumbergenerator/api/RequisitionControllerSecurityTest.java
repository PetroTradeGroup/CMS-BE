package com.couponnumbergenerator.api;

import com.couponnumbergenerator.security.LocationAccessGuard;
import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.CouponRequisitionService;
import com.couponnumbergenerator.service.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A Sales Clerk raises requisitions and sees only their own; fulfil/reject stay with Stocks. */
@WebMvcTest(controllers = RequisitionController.class)
@Import({SecurityConfig.class, LocationAccessGuard.class})
class RequisitionControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponRequisitionService couponRequisitionService;
    @MockitoBean private SecurityAuditService securityAuditService;

    @Test
    void salesClerkListSeesOnlyTheirOwn() throws Exception {
        mockMvc.perform(get("/api/v1/requisitions").with(jwt()
                        .jwt(token -> token.claim("preferred_username", "clerk1"))
                        .authorities(() -> "ROLE_SALES_CLERK")))
                .andExpect(status().isOk());
        verify(couponRequisitionService).getRequisitions(isNull(), eq("clerk1"), any());
    }

    @Test
    void stocksListSeesEveryone() throws Exception {
        mockMvc.perform(get("/api/v1/requisitions").with(jwt()
                        .jwt(token -> token.claim("preferred_username", "stock1"))
                        .authorities(() -> "ROLE_STOCKS_CLERK")))
                .andExpect(status().isOk());
        verify(couponRequisitionService).getRequisitions(isNull(), isNull(), any());
    }

    @Test
    void onlyStocksControllerRejectsARequisition() throws Exception {
        String body = """
                {"decidedBy": "x", "reason": "no"}""";
        for (String role : new String[]{"ROLE_STOCKS_CLERK", "ROLE_ADMIN"}) {
            mockMvc.perform(post("/api/v1/requisitions/1/reject").with(jwt().authorities(() -> role))
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/requisitions/1/reject").with(jwt().authorities(() -> "ROLE_STOCKS_CONTROLLER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    void salesClerkCannotFulfilOrReject() throws Exception {
        // Valid bodies — otherwise @Valid rejects with 400 before the role check gets a say.
        String[][] cases = {
                {"fulfill", """
                        {"batchId": 10, "lines": [{"fuelTypeId": 1, "denomination": 20, "books": 1}]}"""},
                {"auto-fulfill", "{}"},
                {"reject", """
                        {"decidedBy": "clerk1", "reason": "no"}"""}};
        for (String[] c : cases) {
            mockMvc.perform(post("/api/v1/requisitions/1/" + c[0]).with(jwt().authorities(() -> "ROLE_SALES_CLERK"))
                            .contentType("application/json").content(c[1]))
                    .andExpect(status().isForbidden());
        }
    }
}
