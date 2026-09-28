package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.BalanceChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceChangeRepository extends JpaRepository<BalanceChange, Long> {

    List<BalanceChange> findByCreditIdOrderByOccurredAtAscIdAsc(Long creditId);
}
