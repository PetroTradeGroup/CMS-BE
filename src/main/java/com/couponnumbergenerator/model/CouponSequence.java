package com.couponnumbergenerator.model;

import com.couponnumbergenerator.constants.CouponConstants;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coupon_sequences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponSequence {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "fuel_type_id")
    private FuelType fuelType;

    @Column(name = "current_letter", nullable = false, columnDefinition = "char(1) default 'M'")
    private char currentLetter;

    @Column(name = "issued_count", nullable = false, columnDefinition = "bigint default 0")
    private long issuedCount;

    /**
     * Last batch sequence number handed out for this fuel type (0 before the first batch).
     * Batches are numbered 1, 2, 3… per fuel type — the canonical order stock is sold in
     * (see §11.3 of docs/erp-sales-integration-design.md). Lives here rather than in its own
     * table because generation already loads this row {@code FOR UPDATE} in the same
     * transaction that creates the batch, so the increment is free and race-safe.
     */
    @Column(name = "batch_sequence_number", nullable = false, columnDefinition = "bigint default 0")
    private long batchSequenceNumber;

    /** Consumes and returns the next 1-based batch sequence number for this fuel type. */
    public long nextBatchSequenceNumber() {
        return ++batchSequenceNumber;
    }

    public void advanceLetter() {
        if (currentLetter == 'Z') {
            throw new IllegalStateException(
                    "Coupon space exhausted for fuel type %s — all letters M–Z are fully used"
                            .formatted(fuelType.getName()));
        }
        currentLetter = (char) (currentLetter + 1);
        issuedCount = 0;
    }

    public static CouponSequence initialFor(FuelType fuelType) {
        return CouponSequence.builder()
                .fuelType(fuelType)
                .currentLetter(CouponConstants.LETTER_START)
                .issuedCount(0L)
                .batchSequenceNumber(0L)
                .build();
    }
}