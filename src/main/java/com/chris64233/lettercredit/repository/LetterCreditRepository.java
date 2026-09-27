package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.LetterCredit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LetterCreditRepository extends JpaRepository<LetterCredit, Long> {

    Optional<LetterCredit> findByCreditNo(String creditNo);

    boolean existsByCreditNo(String creditNo);

    /** 承兑/撤销事务内对信用证行加写锁，串行化余额变更。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LetterCredit c where c.id = :id")
    Optional<LetterCredit> findByIdForUpdate(@Param("id") Long id);
}
