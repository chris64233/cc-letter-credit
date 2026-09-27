package com.chris64233.lettercredit.repo;

import com.chris64233.lettercredit.domain.Presentation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PresentationRepository extends JpaRepository<Presentation, Long> {

    Optional<Presentation> findByLetterCreditIdAndExternalPresentationNo(Long letterCreditId,
                                                                         String externalPresentationNo);

    List<Presentation> findByLetterCreditIdOrderById(Long letterCreditId);
}
