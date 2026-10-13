package com.couponnumbergenerator.api;

import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.service.InventoryService;
import com.couponnumbergenerator.service.RedemptionSummaryExportService;
import com.couponnumbergenerator.service.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The activity report adds STOCKS_CONTROLLER without opening the rest of inventory to them. */
@WebMvcTest(controllers = InventoryController.class)
@Import(SecurityConfig.class)
class InventoryControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InventoryService inventoryService;
    @MockitoBean private CouponService couponService;
    @MockitoBean private RedemptionSummaryExportService exportService;
    @MockitoBean private SecurityAuditService securityAuditService;

    @Test
    void stocksControllerSeesActivityButNotStockOnHand() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/balance").with(jwt().authorities(() -> "ROLE_STOCKS_CONTROLLER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/balance/trend").with(jwt().authorities(() -> "ROLE_STOCKS_CONTROLLER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/activity").with(jwt().authorities(() -> "ROLE_STOCKS_CONTROLLER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/summary").with(jwt().authorities(() -> "ROLE_STOCKS_CONTROLLER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherRolesCannotSeeActivity() throws Exception {
        for (String role : new String[]{"ROLE_STOCKS_CLERK", "ROLE_SALES_CLERK", "ROLE_ATTENDANT"}) {
            mockMvc.perform(get("/api/v1/inventory/activity").with(jwt().authorities(() -> role)))
                    .andExpect(status().isForbidden());
        }
    }
}
