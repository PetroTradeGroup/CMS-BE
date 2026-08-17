package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.model.CouponSale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponSaleRepository extends JpaRepository<CouponSale, Long> {

    /** The idempotency lookup: has this BC document already been received? */
    Optional<CouponSale> findByBcDocumentNumber(String bcDocumentNumber);

    Page<CouponSale> findByStatus(SaleStatus status, Pageable pageable);
}