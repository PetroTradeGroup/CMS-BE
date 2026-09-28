package com.couponnumbergenerator.api;

import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.BankPurchaseService;
import com.couponnumbergenerator.service.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only a bank's service account (BANK_INTEGRATION) may issue coupons — not staff, not even ADMIN —
 * and the bank is always taken from the token's azp claim, never from the request.
 */
@WebMvcTest(controllers = BankController.class)
@Import(SecurityConfig.class)
class BankControllerSecurityTest {

    private static final String BODY = """
            {"bankReference": "TXN-1", "fuelTypeId": 1, "amount": 31.00,
             "lines": [{"denomination": 20, "count": 1}]}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BankPurchaseService bankPurchaseService;
    @MockitoBean private SecurityAuditService securityAuditService;

    private static RequestPostProcessor bank(String clientId) {
        return jwt().jwt(j -> j.claim("azp", clientId)).authorities(() -> "ROLE_BANK_INTEGRATION");
    }

    @Test
    void noTokenIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/bank/purchases").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotIssueCoupons() throws Exception {
        mockMvc.perform(post("/api/v1/bank/purchases").with(jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchaseIsRecordedAgainstTheCallingBank() throws Exception {
        mockMvc.perform(post("/api/v1/bank/purchases").with(bank("bank-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(bankPurchaseService).purchase(eq("bank-a"), any());
    }

    @Test
    void aBankCannotLookUpAnotherBankByPassingItsCode() throws Exception {
        mockMvc.perform(get("/api/v1/bank/purchases/TXN-1").param("bank", "bank-b").with(bank("bank-a")))
                .andExpect(status().isOk());

        verify(bankPurchaseService).get("bank-a", "TXN-1");
    }

    @Test
    void staffMustSayWhichBank() throws Exception {
        mockMvc.perform(get("/api/v1/bank/purchases/TXN-1").with(jwt().authorities(() -> "ROLE_AUDITOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bankPurchaseService);

        mockMvc.perform(get("/api/v1/bank/purchases/TXN-1").param("bank", "bank-b")
                        .with(jwt().authorities(() -> "ROLE_AUDITOR")))
                .andExpect(status().isOk());
        verify(bankPurchaseService).get("bank-b", "TXN-1");
    }
}
