package com.couponnumbergenerator.lifecycle;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.couponnumbergenerator.enums.CouponStatus.*;

/**
 * Defines the legal coupon lifecycle transitions and the movement type each transition represents.
 * This is the single source of truth for lifecycle rules — all status changes must be validated here.
 */
public final class CouponStateMachine {

    private static final Map<CouponStatus, Set<CouponStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(Map.of(
            GENERATED, Set.of(IN_STOCK, CANCELLED, FLAGGED),
            IN_STOCK, Set.of(IN_TRANSIT, ALLOCATED, EXPIRED, CANCELLED, FLAGGED),
            IN_TRANSIT, Set.of(IN_STOCK, ALLOCATED, EXPIRED, CANCELLED, FLAGGED),
            ALLOCATED, Set.of(REDEEMED, IN_STOCK, EXPIRED, CANCELLED, FLAGGED),
            REDEEMED, Set.of(),
            EXPIRED, Set.of(),
            CANCELLED, Set.of(),
            FLAGGED, Set.of(IN_STOCK, CANCELLED)
    ));

    private CouponStateMachine() {}

    public static boolean canTransition(CouponStatus from, CouponStatus to) {
        return ALLOWED_TRANSITIONS.get(from).contains(to);
    }

    public static boolean isTerminal(CouponStatus status) {
        return ALLOWED_TRANSITIONS.get(status).isEmpty();
    }

    /**
     * Whether a coupon in this status can be relocated (location/department changed) without also
     * changing its status — i.e. it is sitting in stock, available to be moved. Anything else —
     * already allocated, redeemed, flagged, mid-transfer (IN_TRANSIT), or terminal — has left stock
     * and must go through an explicit status change (e.g. receiving it back to IN_STOCK first)
     * before it's eligible to be transferred again.
     */
    public static boolean isInStock(CouponStatus status) {
        return status == IN_STOCK;
    }

    public static Set<CouponStatus> allowedTargets(CouponStatus from) {
        return ALLOWED_TRANSITIONS.get(from);
    }

    /**
     * The movement type recorded in the audit log for a given transition.
     * A null {@code from} means the coupon is being created (GENERATION).
     */
    public static MovementType movementFor(CouponStatus from, CouponStatus to) {
        if (from == null) {
            return MovementType.GENERATION;
        }
        return switch (to) {
            case IN_STOCK -> switch (from) {
                case GENERATED -> MovementType.RECEIPT;
                case IN_TRANSIT -> MovementType.TRANSFER_IN;
                case ALLOCATED -> MovementType.RETURN;
                case FLAGGED -> MovementType.UNFLAG;
                default -> MovementType.ADJUSTMENT;
            };
            case IN_TRANSIT -> MovementType.TRANSFER_OUT;
            case ALLOCATED -> MovementType.ALLOCATION;
            case REDEEMED -> MovementType.REDEMPTION;
            case EXPIRED -> MovementType.EXPIRY;
            case CANCELLED -> MovementType.CANCELLATION;
            case FLAGGED -> MovementType.FLAG;
            case GENERATED -> MovementType.GENERATION;
        };
    }
}