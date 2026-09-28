package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.AmendmentDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AmendmentDecisionRepository extends JpaRepository<AmendmentDecision, Long> {

    /** 决定事件号幂等。 */
    Optional<AmendmentDecision> findByEventNo(String eventNo);

    Optional<AmendmentDecision> findByAmendmentId(Long amendmentId);
}
