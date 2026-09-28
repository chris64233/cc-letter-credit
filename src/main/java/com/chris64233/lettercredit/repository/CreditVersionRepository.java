package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.CreditVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditVersionRepository extends JpaRepository<CreditVersion, Long> {

    List<CreditVersion> findByCreditIdOrderByVersionNoAsc(Long creditId);

    Optional<CreditVersion> findByCreditIdAndVersionNo(Long creditId, int versionNo);

    /** 修订生效事务内锁定当前版本行，串行化版本切换。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from CreditVersion v where v.credit.id = :creditId and v.status = "
            + "com.chris64233.lettercredit.domain.CreditVersionStatus.CURRENT")
    Optional<CreditVersion> findCurrentForUpdate(@Param("creditId") Long creditId);
}
