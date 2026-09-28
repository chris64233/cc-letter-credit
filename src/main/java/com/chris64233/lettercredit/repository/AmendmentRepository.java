package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.Amendment;
import com.chris64233.lettercredit.domain.AmendmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AmendmentRepository extends JpaRepository<Amendment, Long> {

    Optional<Amendment> findByAmendmentNo(String amendmentNo);

    boolean existsByAmendmentNo(String amendmentNo);

    /** 唯一活动修订判定：同一信用证同时至多一笔 PROPOSED。 */
    boolean existsByCreditIdAndStatus(Long creditId, AmendmentStatus status);

    Optional<Amendment> findFirstByCreditIdAndStatus(Long creditId, AmendmentStatus status);

    /** 同一信用证的修订序号，按序号倒序取首条。 */
    List<Amendment> findByCreditIdOrderByAmendmentSeqAsc(Long creditId);

    /** 决定/取消事务内锁定修订行，串行化同一修订的并发决定与取消。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Amendment a where a.id = :id")
    Optional<Amendment> findByIdForUpdate(@Param("id") Long id);
}
