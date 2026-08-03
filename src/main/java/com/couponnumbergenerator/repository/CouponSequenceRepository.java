package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.CouponSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponSequenceRepository extends JpaRepository<CouponSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CouponSequence cs WHERE cs.fuelType.id = :fuelTypeId")
    Optional<CouponSequence> findByFuelTypeIdWithLock(@Param("fuelTypeId") Long fuelTypeId);
}
