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
                .build();
    }
}