package com.couponnumbergenerator.service;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.BankAmountPurchaseRequest;
import com.couponnumbergenerator.dto.request.BankLitresPurchaseRequest;
import com.couponnumbergenerator.dto.request.BankPurchaseRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.GenerateBulkCouponRequest;
import com.couponnumbergenerator.dto.response.BankPurchaseResponse;
import com.couponnumbergenerator.dto.response.BankPurchaseResponse.CatalogItem;
import com.couponnumbergenerator.dto.response.BankPurchaseResponse.Quote;
import com.couponnumbergenerator.dto.response.BankPurchaseResponse.QuoteOption;
import com.couponnumbergenerator.dto.response.BankPurchaseResponse.VirtualCoupon;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.enums.BankPurchaseStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.exception.BankPurchaseNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.model.BankPurchase;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.repository.BankPurchaseRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Virtual coupons sold through a partner bank. The bank takes payment, then posts the purchase
 * here; we mint DIGITAL coupons on demand (no pre-stocking), ALLOCATE them to the customer, and
 * hand back signed QR payloads for the bank to display. Settlement happens offline — this is the
 * record we reconcile against.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankPurchaseService {

    /** Recorded as performedBy on bank-channel coupon movements — there's no human actor. */
    static final String BANK_ACTOR = "BANK-SALE";

    private final BankPurchaseRepository bankPurchaseRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final CouponRepository couponRepository;
    private final CouponService couponService;
    private final CouponLifecycleService couponLifecycleService;
    private final QrCodeService qrCodeService;

    @Transactional(readOnly = true)
    public List<CatalogItem> catalog() {
        return fuelTypeRepository.findAll().stream()
                .filter(ft -> ft.isActive() && ft.getPricePerLitre() != null)
                .map(ft -> new CatalogItem(ft.getId(), ft.getName(), ft.getTypeCode(),
                        ft.getPricePerLitre(), CouponConstants.BANK_CURRENCY))
                .toList();
    }

    /**
     * What a customer's money buys in whole litres: the most litres at or under {@code amount}
     * and the next litre up, each with its exact price, so the bank can offer a round figure
     * before taking payment. An amount that buys an exact whole number of litres gets one option;
     * one too small for a single litre gets only the 1 L option.
     */
    @Transactional(readOnly = true)
    public Quote quote(Long fuelTypeId, BigDecimal amount) {
        FuelType fuelType = onSale(fuelTypeId);
        long under = amount.divide(fuelType.getPricePerLitre(), 0, RoundingMode.FLOOR).longValueExact();
        List<QuoteOption> options = new ArrayList<>(2);
        if (under > 0) {
            options.add(option(under, fuelType));
        }
        if (under == 0 || priceOf(BigDecimal.valueOf(under), fuelType).compareTo(amount) != 0) {
            options.add(option(under + 1, fuelType));
        }
        return new Quote(fuelType.getId(), fuelType.getName(), fuelType.getPricePerLitre(),
                CouponConstants.BANK_CURRENCY, amount, options);
    }

    /**
     * Buy by amount: litres = amount ÷ current price, issued as a single coupon — no denomination
     * breakdown. The amount must buy a whole number of litres; if not, the 400 names the nearest
     * amounts that do (same options as {@link #quote}). Same idempotency as {@link #purchase}.
     */
    @Transactional
    public BankPurchaseResponse purchaseByAmount(String bankCode, BankAmountPurchaseRequest request) {
        List<DenominationLine> lines = List.of();
        // A retry must return the original even if the price has changed since — purchase() does
        // that before it ever reads the lines, so only work out litres for a new purchase.
        if (bankPurchaseRepository.findByBankCodeAndBankReference(bankCode, request.bankReference()).isEmpty()) {
            FuelType fuelType = onSale(request.fuelTypeId());
            BigDecimal[] litres = request.amount().divideAndRemainder(fuelType.getPricePerLitre());
            if (litres[1].signum() != 0) {
                String offers = quote(fuelType.getId(), request.amount()).options().stream()
                        .map(o -> "%s for %d L".formatted(o.amount().toPlainString(), o.litres()))
                        .collect(Collectors.joining(" or "));
                throw new IllegalArgumentException(
                        "%s %s doesn't buy a whole number of litres of %s at %s per litre — charge %s".formatted(
                                request.amount().toPlainString(), CouponConstants.BANK_CURRENCY, fuelType.getName(),
                                fuelType.getPricePerLitre().toPlainString(), offers));
            }
            lines = List.of(new DenominationLine(litres[0], 1));
        }
        return purchase(bankCode, new BankPurchaseRequest(request.bankReference(), request.customerReference(),
                request.fuelTypeId(), lines, request.amount()));
    }

    /**
     * Buy by litres: a whole number of litres as a single coupon; the amount is litres × current
     * price, returned in the response for the bank to charge. A retry returns the original purchase
     * (and its original amount) even if the price has changed since.
     */
    @Transactional
    public BankPurchaseResponse purchaseByLitres(String bankCode, BankLitresPurchaseRequest request) {
        BigDecimal litres = BigDecimal.valueOf(request.litres());
        BankPurchase bankexists = bankPurchaseRepository
                .findByBankCodeAndBankReference(bankCode, request.bankReference()).orElse(null);
        if (bankexists != null) {
            if (!bankexists.getFuelType().getId().equals(request.fuelTypeId())
                    || bankexists.getLitres().compareTo(litres) != 0) {
                throw new IllegalArgumentException(
                        "bankReference %s was already used for a different purchase".formatted(request.bankReference()));
            }
            return toResponse(bankexists);
        }
        FuelType fuelType = onSale(request.fuelTypeId());
        return purchase(bankCode, new BankPurchaseRequest(request.bankReference(), request.customerReference(),
                fuelType.getId(), List.of(new DenominationLine(litres, 1)), priceOf(litres, fuelType)));
    }

    /**
     * Idempotent on (bankCode, bankReference). A concurrent duplicate loses on the unique constraint and gets
     * a 5xx; the bank's retry then hits the idempotent path.
     */
    @Transactional
    public BankPurchaseResponse purchase(String bankCode, BankPurchaseRequest request) {
        BankPurchase bankexists = bankPurchaseRepository.findByBankCodeAndBankReference(bankCode, request.bankReference()).orElse(null);
        if (bankexists != null) {
            if (!bankexists.getFuelType().getId().equals(request.fuelTypeId())
                    || bankexists.getAmount().compareTo(request.amount()) != 0) {
                throw new IllegalArgumentException(
                        "bankReference %s was already used for a different purchase".formatted(request.bankReference()));
            }
            return toResponse(bankexists);
        }

        FuelType fuelType = onSale(request.fuelTypeId());

        request.lines().forEach(DenominationLine::validateQuantity);
        BigDecimal litres = request.lines().stream()
                .map(DenominationLine::resolvedLitres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = priceOf(litres, fuelType);
        if (request.amount().compareTo(expected) != 0) {
            throw new IllegalArgumentException(
                    "amount %s does not match %s L × %s = %s %s — refresh the catalog price".formatted(
                            request.amount().toPlainString(), litres.toPlainString(),
                            fuelType.getPricePerLitre().toPlainString(), expected.toPlainString(),
                            CouponConstants.BANK_CURRENCY));
        }

        List<String> numbers = couponService.generateBulkCoupons(new GenerateBulkCouponRequest(
                        fuelType.getId(), null, request.lines(), null, null, CouponType.DIGITAL, null, BANK_ACTOR))
                .stream().map(CouponResponse::couponNumber).toList();
        List<Coupon> coupons = couponRepository.findByCouponNumberIn(numbers);

        BankPurchase purchase = bankPurchaseRepository.save(BankPurchase.builder()
                .bankCode(bankCode)
                .bankReference(request.bankReference())
                .customerReference(request.customerReference())
                .fuelType(fuelType)
                .batch(coupons.getFirst().getBatch())
                .litres(litres)
                .pricePerLitre(fuelType.getPricePerLitre())
                .amount(expected)
                .status(BankPurchaseStatus.ISSUED)
                .build());
        couponLifecycleService.transitionForBankPurchase(coupons, CouponStatus.ALLOCATED,
                "Bank purchase %s/%s".formatted(bankCode, purchase.getBankReference()), BANK_ACTOR, purchase.getId());

        log.info("Bank {} purchase {} issued {} {} coupon(s), {} L for {} {}", bankCode, purchase.getBankReference(),
                coupons.size(), fuelType.getName(), litres, expected, CouponConstants.BANK_CURRENCY);
        return toResponse(purchase);
    }

    @Transactional(readOnly = true)
    public BankPurchaseResponse get(String bankCode, String bankReference) {
        return toResponse(find(bankCode, bankReference));
    }

    /**
     * Cancels every coupon of the purchase, all-or-nothing: if any has already been redeemed (or
     * expired) the state machine refuses and nothing changes (409). Reversing twice is a no-op.
     */
    // ponytail: whole-purchase only; add per-coupon reversal if the bank needs partial refunds.
    @Transactional
    public BankPurchaseResponse reverse(String bankCode, String bankReference, String reason) {
        BankPurchase purchase = find(bankCode, bankReference);
        if (purchase.getStatus() == BankPurchaseStatus.REVERSED) {
            return toResponse(purchase);
        }
        couponLifecycleService.transitionForBankPurchase(coupons(purchase), CouponStatus.CANCELLED,
                reason, BANK_ACTOR, purchase.getId());
        purchase.setStatus(BankPurchaseStatus.REVERSED);
        purchase.setReversalReason(reason);
        purchase.setReversedAt(LocalDateTime.now());
        log.info("Bank {} purchase {} reversed: {}", bankCode, bankReference, reason);
        return toResponse(bankPurchaseRepository.save(purchase));
    }

    /** Scoped to one bank: another bank's reference is simply not found. */
    private BankPurchase find(String bankCode, String bankReference) {
        return bankPurchaseRepository.findByBankCodeAndBankReference(bankCode, bankReference)
                .orElseThrow(() -> new BankPurchaseNotFoundException(bankCode, bankReference));
    }

    private FuelType onSale(Long fuelTypeId) {
        FuelType fuelType = fuelTypeRepository.findById(fuelTypeId)
                .orElseThrow(() -> new FuelTypeNotFoundException(fuelTypeId));
        if (!fuelType.isActive() || fuelType.getPricePerLitre() == null) {
            throw new IllegalArgumentException("Fuel type %s is not on sale".formatted(fuelType.getName()));
        }
        return fuelType;
    }

    /** The one place litres become money: litres × price, rounded half-up to cents. */
    private static BigDecimal priceOf(BigDecimal litres, FuelType fuelType) {
        return litres.multiply(fuelType.getPricePerLitre()).setScale(2, RoundingMode.HALF_UP);
    }

    private static QuoteOption option(long litres, FuelType fuelType) {
        return new QuoteOption(litres, priceOf(BigDecimal.valueOf(litres), fuelType));
    }

    private List<Coupon> coupons(BankPurchase purchase) {
        return couponRepository.findByBatchIdOrderByBatchSequenceAsc(purchase.getBatch().getId());
    }

    private BankPurchaseResponse toResponse(BankPurchase purchase) {
        List<VirtualCoupon> coupons = coupons(purchase).stream()
                .map(c -> new VirtualCoupon(c.getCouponNumber(), c.getRedemptionCode(), c.getDenomination(), c.getStatus(),
                        c.getExpiryDate(), qrCodeService.buildSignedPayload(c)))
                .toList();
        return new BankPurchaseResponse(purchase.getBankCode(), purchase.getBankReference(), purchase.getCustomerReference(),
                purchase.getStatus(), purchase.getFuelType().getName(), purchase.getLitres(),
                purchase.getPricePerLitre(), purchase.getAmount(), CouponConstants.BANK_CURRENCY,
                purchase.getCreatedAt(), purchase.getReversedAt(), purchase.getReversalReason(), coupons);
    }
}
