package com.chris64233.lettercredit.repo;

import com.chris64233.lettercredit.domain.Acceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcceptanceRepository extends JpaRepository<Acceptance, Long> {

    Optional<Acceptance> findByPresentationId(Long presentationId);

    List<Acceptance> findByPresentationLetterCreditIdOrderById(Long letterCreditId);
}
