package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CreateAttendantRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.AttendantResponse;
import com.couponnumbergenerator.enums.UserSyncStatus;
import com.couponnumbergenerator.service.impl.AttendantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.ATTENDANTS_PATH)
@Tag(name = "Attendants", description = "Team Leader registers Attendant accounts. Role is always ATTENDANT — "
        + "fixed server-side — and locationCode may be any existing station, not just the caller's own.")
public class AttendantController {

    private final AttendantService attendantService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEAM_LEADER','ADMIN')")
    @Operation(summary = "Register a new attendant",
            description = "Saves the attendant locally first (durable even if Keycloak is down), then creates "
                    + "the Keycloak account (role fixed to ATTENDANT, group /RETAIL) with the given locationCode. "
                    + "If Keycloak is reachable, returns 201 with a one-time temporary password the Team Leader "
                    + "must hand to the attendant. If Keycloak is unreachable, returns 202: the registration is "
                    + "queued and synced automatically in the background — call POST "
                    + ".../{id}/reset-temp-password once it shows SYNCED to obtain a password.")
    public ResponseEntity<ApiResponse<AttendantResponse>> createAttendant(
            @Valid @RequestBody CreateAttendantRequest request) {
        AttendantResponse response = attendantService.registerAttendant(request);
        if (response.syncStatus() == UserSyncStatus.SYNCED) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Attendant %s registered at %s"
                            .formatted(response.username(), response.locationCode()), response));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Attendant %s queued for registration at %s — Keycloak is unreachable, "
                        + "it will be provisioned automatically. Use POST %s/%d/reset-temp-password once synced "
                        + "to get a login password."
                        .formatted(response.username(), response.locationCode(),
                                CouponConstants.ATTENDANTS_PATH, response.id()),
                        response));
    }

    @PostMapping("/{id}/reset-temp-password")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','ADMIN')")
    @Operation(summary = "Issue a fresh one-time password for an already-synced attendant",
            description = "For an attendant whose original temporary password was never handed out — most "
                    + "commonly one that finished syncing to Keycloak in the background after being queued while "
                    + "Keycloak was down. 409 if the attendant hasn't synced to Keycloak yet.")
    public ResponseEntity<ApiResponse<AttendantResponse>> resetTemporaryPassword(@PathVariable Long id) {
        AttendantResponse response = attendantService.resetTemporaryPassword(id);
        return ResponseEntity.ok(ApiResponse.success(
                "New temporary password issued for %s".formatted(response.username()), response));
    }
}
