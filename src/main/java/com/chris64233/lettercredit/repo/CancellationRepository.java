package com.chris64233.lettercredit.repo;

import com.chris64233.lettercredit.domain.Cancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

    Optional<Cancellation> findByAcceptanceId(Long acceptanceId);

    boolean existsByAcceptanceId(Long acceptanceId);
}
