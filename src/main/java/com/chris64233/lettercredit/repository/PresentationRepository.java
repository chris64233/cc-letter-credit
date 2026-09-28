package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.PresentationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresentationRepository extends JpaRepository<Presentation, Long> {

    Optional<Presentation> findByPresentationNo(String presentationNo);

    boolean existsByPresentationNo(String presentationNo);

    List<Presentation> findByCreditIdOrderByPresentationDateAscIdAsc(Long creditId);

    /** 某信用证版本下状态为给定值的交单。 */
    List<Presentation> findByCreditIdAndCreditVersionNoAndStatus(
            Long creditId, int creditVersionNo, PresentationStatus status);

    /** 承兑事务内锁定交单行，串行化同一交单的并发承兑（幂等）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Presentation p where p.id = :id")
    Optional<Presentation> findByIdForUpdate(@Param("id") Long id);
}
