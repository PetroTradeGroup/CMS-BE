package com.couponnumbergenerator.api;

import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.QrCodeService;
import com.couponnumbergenerator.service.RedemptionReportService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only station staff (Attendant/Team Leader) look up and redeem coupons — not stocks or admin. */
@WebMvcTest(controllers = RedemptionController.class)
@Import(SecurityConfig.class)
class RedemptionControllerSecurityTest {

    private static final String BODY = """
            {"redemptionCodes": ["K7M4QX2"], "carRegistrationNumber": "ABC1234"}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponLifecycleService couponLifecycleService;
    @MockitoBean private RedemptionReportService redemptionReportService;
    @MockitoBean private RedemptionSummaryExportService redemptionSummaryExportService;
    @MockitoBean private QrCodeService qrCodeService;
    @MockitoBean private SecurityAuditService securityAuditService;

    @Test
    void adminAndStocksCannotRedeem() throws Exception {
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_STOCKS_CLERK", "ROLE_STOCKS_CONTROLLER"}) {
            mockMvc.perform(post("/api/v1/redemptions").with(jwt().authorities(() -> role))
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void stocksCannotVerifyCodes() throws Exception {
        for (String role : new String[]{"ROLE_STOCKS_CLERK", "ROLE_STOCKS_CONTROLLER"}) {
            mockMvc.perform(get("/api/v1/redemptions/scan/code/K7M4QX2").with(jwt().authorities(() -> role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void attendantTeamLeaderAndAdminCanVerifyCodes() throws Exception {
        for (String role : new String[]{"ROLE_ATTENDANT", "ROLE_TEAM_LEADER", "ROLE_ADMIN"}) {
            mockMvc.perform(get("/api/v1/redemptions/scan/code/K7M4QX2").with(jwt().authorities(() -> role)))
                    .andExpect(status().isOk());
        }
    }
}
