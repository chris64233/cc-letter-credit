package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.AmendmentDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AmendmentDecisionRepository extends JpaRepository<AmendmentDecision, Long> {

    Optional<AmendmentDecision> findByAmendmentId(Long amendmentId);

    boolean existsByDecisionEventNo(String decisionEventNo);

    Optional<AmendmentDecision> findByDecisionEventNo(String decisionEventNo);
}
