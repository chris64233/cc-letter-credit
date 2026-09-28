package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.BalanceMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceMovementRepository extends JpaRepository<BalanceMovement, Long> {

    List<BalanceMovement> findByCreditIdOrderByOccurredAtAscIdAsc(Long creditId);
}
