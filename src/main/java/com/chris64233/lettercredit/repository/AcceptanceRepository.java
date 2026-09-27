package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.Acceptance;
import com.chris64233.lettercredit.domain.AcceptanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcceptanceRepository extends JpaRepository<Acceptance, Long> {

    Optional<Acceptance> findByAcceptanceNo(String acceptanceNo);

    /** 交单号幂等：同一交单至多一条承兑。 */
    Optional<Acceptance> findByPresentationId(Long presentationId);

    Optional<Acceptance> findByPresentationPresentationNo(String presentationNo);

    List<Acceptance> findByCreditIdOrderByAcceptedAtAsc(Long creditId);

    List<Acceptance> findByCreditIdAndStatusOrderByAcceptedAtAsc(Long creditId, AcceptanceStatus status);
}
