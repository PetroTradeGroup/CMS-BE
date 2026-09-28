package com.couponnumbergenerator.api;

import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.CouponSaleService;
import com.couponnumbergenerator.service.SecurityAuditService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Business Central webhook authenticates with OAuth2 Client Credentials: the token's service
 * account must carry the ERP_INTEGRATION realm role. Staff roles — even ADMIN — must not be able
 * to post sales, and no token is a 401.
 */
@WebMvcTest(controllers = ErpSaleController.class)
@Import(SecurityConfig.class)
class ErpSaleControllerSecurityTest {

    private static final String SALE_BODY = """
            {"documentNumber": "SO-1", "locationCode": "HQ",
             "lines": [{"fuelTypeId": 1, "denomination": 20, "quantity": 5}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponSaleService couponSaleService;

    // WebConfig (pulled into every @WebMvcTest slice as a WebMvcConfigurer) wires
    // SecurityAuditInterceptor, which needs this.
    @MockitoBean
    private SecurityAuditService securityAuditService;

    @Test
    void noTokenIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/erp/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffRoleCannotPostASale() throws Exception {
        mockMvc.perform(post("/api/v1/erp/sales")
                        .with(jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void erpIntegrationRoleCanPostASale() throws Exception {
        when(couponSaleService.receiveSale(any())).thenReturn(new CouponSaleResponse(
                1L, "SO-1", null, SaleStatus.ASSIGNED, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/erp/sales")
                        .with(jwt().authorities(() -> "ROLE_ERP_INTEGRATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALE_BODY))
                .andExpect(status().isOk());
    }
}
