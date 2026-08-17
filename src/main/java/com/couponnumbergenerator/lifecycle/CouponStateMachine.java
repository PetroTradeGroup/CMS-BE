package com.couponnumbergenerator.lifecycle;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.couponnumbergenerator.enums.CouponStatus.*;


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


    public static boolean isInStock(CouponStatus status) {
        return status == IN_STOCK;
    }

    public static Set<CouponStatus> allowedTargets(CouponStatus from) {
        return ALLOWED_TRANSITIONS.get(from);
    }


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