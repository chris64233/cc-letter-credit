package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.DiscrepancyDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscrepancyDecisionRepository extends JpaRepository<DiscrepancyDecision, Long> {

    Optional<DiscrepancyDecision> findByReviewVersionId(Long reviewVersionId);
}
