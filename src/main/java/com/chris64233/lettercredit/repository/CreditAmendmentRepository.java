package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.AmendmentStatus;
import com.chris64233.lettercredit.domain.CreditAmendment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditAmendmentRepository extends JpaRepository<CreditAmendment, Long> {

    Optional<CreditAmendment> findByAmendmentNo(String amendmentNo);

    List<CreditAmendment> findByCreditIdOrderByProposedAtAsc(Long creditId);

    /** 活动修订查询：同一信用证同时至多一笔 PROPOSED。 */
    Optional<CreditAmendment> findByCreditIdAndStatus(Long creditId, AmendmentStatus status);

    boolean existsByCreditIdAndStatus(Long creditId, AmendmentStatus status);

    /** 决定/取消事务内锁定修订单行，串行化并发决定。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAmendment a where a.id = :id")
    Optional<CreditAmendment> findByIdForUpdate(@Param("id") Long id);
}
