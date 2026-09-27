package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.ReviewVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewVersionRepository extends JpaRepository<ReviewVersion, Long> {

    List<ReviewVersion> findByPresentationIdOrderByVersionNoAsc(Long presentationId);

    Optional<ReviewVersion> findByPresentationIdAndVersionNo(Long presentationId, int versionNo);
}
