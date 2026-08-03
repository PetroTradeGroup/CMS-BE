package com.couponnumbergenerator.lifecycle;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.couponnumbergenerator.enums.CouponStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class CouponStateMachineTest {

    /** Mirror of the business rules — any change to the state machine must be a conscious edit here too. */
    private static final Map<CouponStatus, Set<CouponStatus>> EXPECTED = Map.of(
            GENERATED, Set.of(IN_STOCK, CANCELLED, FLAGGED),
            IN_STOCK, Set.of(IN_TRANSIT, ALLOCATED, EXPIRED, CANCELLED, FLAGGED),
            IN_TRANSIT, Set.of(IN_STOCK, ALLOCATED, EXPIRED, CANCELLED, FLAGGED),
            ALLOCATED, Set.of(REDEEMED, IN_STOCK, EXPIRED, CANCELLED, FLAGGED),
            REDEEMED, Set.of(),
            EXPIRED, Set.of(),
            CANCELLED, Set.of(),
            FLAGGED, Set.of(IN_STOCK, CANCELLED)
    );

    @Test
    void everyFromToPairMatchesTheBusinessRules() {
        for (CouponStatus from : CouponStatus.values()) {
            for (CouponStatus to : CouponStatus.values()) {
                assertThat(CouponStateMachine.canTransition(from, to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(EXPECTED.get(from).contains(to));
            }
        }
    }

    @Test
    void redeemedExpiredAndCancelledAreTerminal() {
        assertThat(CouponStateMachine.isTerminal(REDEEMED)).isTrue();
        assertThat(CouponStateMachine.isTerminal(EXPIRED)).isTrue();
        assertThat(CouponStateMachine.isTerminal(CANCELLED)).isTrue();
        assertThat(CouponStateMachine.isTerminal(GENERATED)).isFalse();
        assertThat(CouponStateMachine.isTerminal(IN_STOCK)).isFalse();
        assertThat(CouponStateMachine.isTerminal(IN_TRANSIT)).isFalse();
        assertThat(CouponStateMachine.isTerminal(ALLOCATED)).isFalse();
        assertThat(CouponStateMachine.isTerminal(FLAGGED)).isFalse();
    }

    @Test
    void movementTypeReflectsTheSemanticsOfEachTransition() {
        assertThat(CouponStateMachine.movementFor(null, GENERATED)).isEqualTo(MovementType.GENERATION);
        assertThat(CouponStateMachine.movementFor(null, IN_STOCK)).isEqualTo(MovementType.GENERATION);
        assertThat(CouponStateMachine.movementFor(GENERATED, IN_STOCK)).isEqualTo(MovementType.RECEIPT);
        assertThat(CouponStateMachine.movementFor(IN_STOCK, IN_TRANSIT)).isEqualTo(MovementType.TRANSFER_OUT);
        assertThat(CouponStateMachine.movementFor(IN_TRANSIT, IN_STOCK)).isEqualTo(MovementType.TRANSFER_IN);
        assertThat(CouponStateMachine.movementFor(IN_TRANSIT, ALLOCATED)).isEqualTo(MovementType.ALLOCATION);
        assertThat(CouponStateMachine.movementFor(IN_TRANSIT, EXPIRED)).isEqualTo(MovementType.EXPIRY);
        assertThat(CouponStateMachine.movementFor(IN_STOCK, ALLOCATED)).isEqualTo(MovementType.ALLOCATION);
        assertThat(CouponStateMachine.movementFor(ALLOCATED, IN_STOCK)).isEqualTo(MovementType.RETURN);
        assertThat(CouponStateMachine.movementFor(ALLOCATED, REDEEMED)).isEqualTo(MovementType.REDEMPTION);
        assertThat(CouponStateMachine.movementFor(IN_STOCK, EXPIRED)).isEqualTo(MovementType.EXPIRY);
        assertThat(CouponStateMachine.movementFor(IN_STOCK, CANCELLED)).isEqualTo(MovementType.CANCELLATION);
        assertThat(CouponStateMachine.movementFor(IN_STOCK, FLAGGED)).isEqualTo(MovementType.FLAG);
        assertThat(CouponStateMachine.movementFor(FLAGGED, IN_STOCK)).isEqualTo(MovementType.UNFLAG);
    }
}