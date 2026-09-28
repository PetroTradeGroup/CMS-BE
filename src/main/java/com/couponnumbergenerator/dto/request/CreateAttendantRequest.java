package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers a new attendant account. The role is always ATTENDANT — fixed server-side, not
 * chosen by the Team Leader — so this endpoint can never be used to create a privileged account.
 *
 * <p>{@code locationCode} is optional: omit it and the attendant is registered at the caller's
 * own station (derived from their token, the common case — no picker needed). Supply it
 * explicitly to register staff for a different station instead; a caller with no station of
 * their own (e.g. Admin) must supply it.
 */
public record CreateAttendantRequest(
        @NotBlank(message = "username is required")
        @Size(max = 50, message = "username must be at most 50 characters")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "firstName is required")
        @Size(max = 50, message = "firstName must be at most 50 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 50, message = "lastName must be at most 50 characters")
        String lastName,

        @Size(max = 10, message = "locationCode must be at most 10 characters")
        String locationCode
) {}
