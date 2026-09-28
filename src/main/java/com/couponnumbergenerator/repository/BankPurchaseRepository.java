package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.BankPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankPurchaseRepository extends JpaRepository<BankPurchase, Long> {

    Optional<BankPurchase> findByBankCodeAndBankReference(String bankCode, String bankReference);
}
