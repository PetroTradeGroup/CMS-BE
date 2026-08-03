package com.couponnumbergenerator.api;

import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.TransitionResultResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.exception.InvalidStatusTransitionException;
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponController.class)
class CouponControllerTransitionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;

    @MockitoBean
    private CouponLifecycleService couponLifecycleService;

    @Test
    void emptyCouponListFailsValidationWithFieldErrorShape() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponNumbers": [], "targetStatus": "CANCELLED", "reason": "damaged"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.couponNumbers").exists());
    }

    @Test
    void missingTargetStatusFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponNumbers": ["PU002M0000001"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.targetStatus").exists());
    }

    @Test
    void missingReasonForCancellationReturnsBadRequest() throws Exception {
        when(couponLifecycleService.transition(any()))
                .thenThrow(new IllegalArgumentException("A reason is required when transitioning coupons to CANCELLED"));

        mockMvc.perform(post("/api/v1/coupons/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponNumbers": ["PU002M0000001"], "targetStatus": "CANCELLED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("A reason is required when transitioning coupons to CANCELLED"));
    }

    @Test
    void illegalTransitionReturnsConflict() throws Exception {
        when(couponLifecycleService.transition(any())).thenThrow(
                new InvalidStatusTransitionException("PU002M0000001", CouponStatus.REDEEMED, CouponStatus.IN_STOCK));

        mockMvc.perform(post("/api/v1/coupons/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponNumbers": ["PU002M0000001"], "targetStatus": "IN_STOCK"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void successfulTransitionReturnsResult() throws Exception {
        when(couponLifecycleService.transition(any()))
                .thenReturn(new ActionOutcome.Applied<>(new TransitionResultResponse(2, CouponStatus.ALLOCATED)));

        mockMvc.perform(post("/api/v1/coupons/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponNumbers": ["PU002M0000001", "PU002M0000002"], "targetStatus": "ALLOCATED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.targetStatus").value("ALLOCATED"));
    }

    @Test
    void transitionWithLocationReturnsAcceptedForApproval() throws Exception {
        ApprovalRequestResponse pendingRequest = new ApprovalRequestResponse(
                5L, ApprovalRequestType.TRANSITION, 1, List.of(), List.of(), List.of("PU002M0000001"), null, null, null,
                CouponStatus.ALLOCATED, null, null, null, "tester", LocalDateTime.now(),
                ApprovalStatus.PENDING, null, null, null, null, null, null, List.of());
        when(couponLifecycleService.transition(any()))
                .thenReturn(new ActionOutcome.Pending<>(pendingRequest));

        mockMvc.perform(post("/api/v1/coupons/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponNumbers": ["PU002M0000001"], "targetStatus": "ALLOCATED", "toLocationId": 2}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}