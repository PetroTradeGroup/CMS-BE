package com.couponnumbergenerator.api;

import com.couponnumbergenerator.config.OpenApiConfig;
import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.BankAmountPurchaseRequest;
import com.couponnumbergenerator.dto.request.BankLitresPurchaseRequest;
import com.couponnumbergenerator.dto.request.BankPurchaseRequest;
import com.couponnumbergenerator.dto.request.BankReversalRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.BankPurchaseResponse;
import com.couponnumbergenerator.service.BankPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Each bank is its own Keycloak client carrying the BANK_INTEGRATION role; the token's azp claim
 * (the client id) identifies the bank. Onboarding a bank = creating a client, no code change. A
 * bank only ever sees and reverses its own purchases.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.BANK_PATH)
@SecurityRequirement(name = OpenApiConfig.CLIENT_CREDENTIALS_SCHEME)
@Tag(name = "Bank", description = "Virtual coupons sold through partner banks (OAuth2 Client Credentials, "
        + "BANK_INTEGRATION role, one Keycloak client per bank). The bank takes payment, posts the purchase, "
        + "and displays the returned QR payloads and redemption codes to its customer; settlement with "
        + "Petrotrade is offline.")
public class BankController {

    private static final String BANK_ROLE = "ROLE_BANK_INTEGRATION";

    private final BankPurchaseService bankPurchaseService;

    @GetMapping("/catalog")
    @PreAuthorize("hasRole('BANK_INTEGRATION')")
    @Operation(summary = "Fuel types on sale and their current price per litre")
    public ResponseEntity<ApiResponse<List<BankPurchaseResponse.CatalogItem>>> catalog() {
        return ResponseEntity.ok(ApiResponse.success(bankPurchaseService.catalog()));
    }

    @PostMapping("/purchases")
    @PreAuthorize("hasRole('BANK_INTEGRATION')")
    @Operation(summary = "Issue virtual coupons for a paid purchase",
            description = "Mints DIGITAL coupons, one per requested denomination × count, and allocates them "
                    + "to the customer. amount must equal litres × current pricePerLitre (else 400). Idempotent "
                    + "on bankReference within the calling bank: re-posting returns the original purchase; "
                    + "reusing it for a different fuel type or amount is a 400.")
    public ResponseEntity<ApiResponse<BankPurchaseResponse>> purchase(Authentication authentication,
                                                                      @Valid @RequestBody BankPurchaseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankPurchaseService.purchase(bankOf(authentication), request)));
    }

    @GetMapping("/quote")
    @PreAuthorize("hasRole('BANK_INTEGRATION')")
    @Operation(summary = "What an amount of money buys in whole litres",
            description = "Call before taking payment. Returns the most whole litres the amount covers and the "
                    + "next litre up, each with its exact price — e.g. 50.00 of diesel at 1.55 → 32 L for 49.60 "
                    + "or 33 L for 51.15. An amount that buys an exact whole number of litres returns just that "
                    + "option. Charge the customer the chosen option's amount, then POST /bank/purchases/by-amount with it.")
    public ResponseEntity<ApiResponse<BankPurchaseResponse.Quote>> quote(@RequestParam Long fuelTypeId,
                                                                         @RequestParam BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return ResponseEntity.ok(ApiResponse.success(bankPurchaseService.quote(fuelTypeId, amount)));
    }

    @PostMapping("/purchases/by-amount")
    @PreAuthorize("hasRole('BANK_INTEGRATION')")
    @Operation(summary = "Buy fuel by amount of money — we work out the litres",
            description = "Send bankReference, fuelTypeId and amount; the response carries the litres (one coupon, "
                    + "no denominations). The amount must buy a whole number of litres at the current price — "
                    + "otherwise 400 naming the nearest amounts that do, e.g. \"charge 49.60 for 32 L or 51.15 for "
                    + "33 L\" (GET /bank/quote gives the same options before taking payment). Idempotent on "
                    + "bankReference within the calling bank, shared with POST /bank/purchases. The customer uses "
                    + "the whole quantity in one fill.")
    public ResponseEntity<ApiResponse<BankPurchaseResponse>> purchaseByAmount(
            Authentication authentication, @Valid @RequestBody BankAmountPurchaseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankPurchaseService.purchaseByAmount(bankOf(authentication), request)));
    }

    @PostMapping("/purchases/litres")
    @PreAuthorize("hasRole('BANK_INTEGRATION')")
    @Operation(summary = "Buy a whole number of litres — we work out the amount",
            description = "Send bankReference, fuelTypeId and litres (a whole number); the response carries the "
                    + "amount to charge (litres × current pricePerLitre) and one coupon for the litres, no "
                    + "denominations. Idempotent on bankReference within the calling bank, shared with the other "
                    + "purchase endpoints; a retry returns the original amount even if the price has changed. The "
                    + "customer uses the whole quantity in one fill.")
    public ResponseEntity<ApiResponse<BankPurchaseResponse>> purchaseByLitres(
            Authentication authentication, @Valid @RequestBody BankLitresPurchaseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankPurchaseService.purchaseByLitres(bankOf(authentication), request)));
    }

    @GetMapping("/purchases/{bankReference}")
    @PreAuthorize("hasAnyRole('BANK_INTEGRATION','ADMIN','AUDITOR')")
    @Operation(summary = "A purchase with each coupon's current status (e.g. whether it's been redeemed)",
            description = "A bank sees only its own purchases (404 otherwise). ADMIN/AUDITOR must say which "
                    + "bank with ?bank=<Keycloak client id>.")
    public ResponseEntity<ApiResponse<BankPurchaseResponse>> get(
            Authentication authentication,
            @PathVariable String bankReference,
            @Parameter(description = "Staff only: the bank's Keycloak client id, e.g. bank-integration")
            @RequestParam(required = false) String bank) {
        String bankCode = isBank(authentication) ? bankOf(authentication) : bank;
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException("bank is required — the bank's Keycloak client id");
        }
        return ResponseEntity.ok(ApiResponse.success(bankPurchaseService.get(bankCode, bankReference)));
    }

    @PostMapping("/purchases/{bankReference}/reverse")
    @PreAuthorize("hasRole('BANK_INTEGRATION')")
    @Operation(summary = "Reverse a purchase (refund / failed payment)",
            description = "Cancels every coupon, all-or-nothing. 409 if any coupon is already redeemed or "
                    + "expired. Reversing an already-reversed purchase returns it unchanged.")
    public ResponseEntity<ApiResponse<BankPurchaseResponse>> reverse(Authentication authentication,
                                                                     @PathVariable String bankReference,
                                                                     @Valid @RequestBody BankReversalRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                bankPurchaseService.reverse(bankOf(authentication), bankReference, request.reason())));
    }

    private static boolean isBank(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> BANK_ROLE.equals(a.getAuthority()));
    }

    /** The calling bank, from the token — never from the request body, which the bank controls. */
    private static String bankOf(Authentication authentication) {
        String azp = authentication.getPrincipal() instanceof Jwt jwt ? jwt.getClaimAsString("azp") : null;
        if (azp == null || azp.isBlank()) {
            throw new AccessDeniedException("Token carries no client id (azp) — can't tell which bank is calling");
        }
        return azp;
    }
}
