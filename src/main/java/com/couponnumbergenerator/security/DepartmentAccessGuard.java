package com.couponnumbergenerator.security;

import com.couponnumbergenerator.exception.DepartmentAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class DepartmentAccessGuard {

    public void assertDepartment(String expectedCode, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String callerDepartment = jwt.getClaimAsString("department");
        if (!expectedCode.equals(callerDepartment)) {
            throw new DepartmentAccessDeniedException(
                    "Caller's department (%s) does not match the required department (%s)"
                            .formatted(callerDepartment, expectedCode));
        }
    }
}
