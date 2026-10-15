package com.couponnumbergenerator.api;

import com.couponnumbergenerator.security.SecurityConfig;
import com.couponnumbergenerator.service.LocationService;
import com.couponnumbergenerator.service.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin and Regional Rep manage companies and which company a site is under; nobody else. */
@WebMvcTest(controllers = {CompanyController.class, LocationController.class})
@Import(SecurityConfig.class)
class CompanyControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private LocationService locationService;
    @MockitoBean private SecurityAuditService securityAuditService;

    @Test
    void adminAndRegionalRepManageCompaniesOthersCannot() throws Exception {
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_REGIONAL_REP", "ROLE_RETAIL_MANAGER"}) {
            mockMvc.perform(post("/api/v1/companies").with(jwt().authorities(() -> role))
                            .contentType("application/json").content("""
                                    {"code": "REDAN", "name": "Redan"}"""))
                    .andExpect(status().isCreated());
            mockMvc.perform(put("/api/v1/locations/3/company").with(jwt().authorities(() -> role))
                            .contentType("application/json").content("""
                                    {"companyId": 7}"""))
                    .andExpect(status().isOk());
        }
        for (String role : new String[]{"ROLE_STOCKS_CLERK", "ROLE_TEAM_LEADER"}) {
            mockMvc.perform(post("/api/v1/companies").with(jwt().authorities(() -> role))
                            .contentType("application/json").content("""
                                    {"code": "REDAN", "name": "Redan"}"""))
                    .andExpect(status().isForbidden());
        }
    }
}
