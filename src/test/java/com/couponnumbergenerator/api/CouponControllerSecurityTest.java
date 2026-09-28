package com.couponnumbergenerator.api;

import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.service.SecurityAuditService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves AD-1/AD-2 actually gate a representative endpoint (POST /coupons/generate): no token,
 * wrong role, and the right role. Unlike CouponControllerTransitionTest, this slice imports the
 * real SecurityConfig instead of disabling it.
 */
@WebMvcTest(controllers = CouponController.class)
@Import(SecurityConfig.class)
class CouponControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;

    @MockitoBean
    private CouponLifecycleService couponLifecycleService;

    // WebConfig (pulled into every @WebMvcTest slice as a WebMvcConfigurer) wires
    // SecurityAuditInterceptor, which needs this.
    @MockitoBean
    private SecurityAuditService securityAuditService;

    private static final String GENERATE_BODY = """
            {"fuelTypeId": 1, "denomination": 20}
            """;

    @Test
    void noTokenIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GENERATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleIsRejectedWithForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/generate")
                        .with(jwt().authorities(() -> "ROLE_AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GENERATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void stocksClerkCanGenerateACoupon() throws Exception {
        when(couponService.generateCoupon(any())).thenReturn(new CouponResponse(
                1L, "PU002M0000001", null, CouponStatus.GENERATED, new BigDecimal("20"),
                null, null, null, null, null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/coupons/generate")
                        .with(jwt().authorities(() -> "ROLE_STOCKS_CLERK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GENERATE_BODY))
                .andExpect(status().isCreated());
    }
}
