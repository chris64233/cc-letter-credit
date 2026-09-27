package com.chris64233.lettercredit.repo;

import com.chris64233.lettercredit.domain.LetterCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LetterCreditRepository extends JpaRepository<LetterCredit, Long> {

    Optional<LetterCredit> findByLcNumber(String lcNumber);
}
