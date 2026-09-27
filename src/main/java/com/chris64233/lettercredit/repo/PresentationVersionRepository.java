package com.chris64233.lettercredit.repo;

import com.chris64233.lettercredit.domain.PresentationVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PresentationVersionRepository extends JpaRepository<PresentationVersion, Long> {

    List<PresentationVersion> findByPresentationIdOrderByVersionNo(Long presentationId);

    Optional<PresentationVersion> findByPresentationIdAndVersionNo(Long presentationId, int versionNo);
}
