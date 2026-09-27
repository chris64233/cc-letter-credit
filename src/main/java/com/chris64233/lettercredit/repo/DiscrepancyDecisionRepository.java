package com.chris64233.lettercredit.repo;

import com.chris64233.lettercredit.domain.DiscrepancyDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscrepancyDecisionRepository extends JpaRepository<DiscrepancyDecision, Long> {

    List<DiscrepancyDecision> findByPresentationIdOrderById(Long presentationId);

    Optional<DiscrepancyDecision> findFirstByPresentationIdAndVersionNoOrderByIdDesc(Long presentationId,
                                                                                     int versionNo);
}
