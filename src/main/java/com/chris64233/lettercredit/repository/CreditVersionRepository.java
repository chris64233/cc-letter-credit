package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.CreditVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditVersionRepository extends JpaRepository<CreditVersion, Long> {

    List<CreditVersion> findByCreditIdOrderByVersionNoAsc(Long creditId);

    Optional<CreditVersion> findByCreditIdAndVersionNo(Long creditId, int versionNo);
}
