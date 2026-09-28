package com.chris64233.lettercredit.repository;

import com.chris64233.lettercredit.domain.Acceptance;
import com.chris64233.lettercredit.domain.AcceptanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AcceptanceRepository extends JpaRepository<Acceptance, Long> {

    Optional<Acceptance> findByAcceptanceNo(String acceptanceNo);

    /** 交单号幂等：同一交单至多一条承兑。 */
    Optional<Acceptance> findByPresentationId(Long presentationId);

    Optional<Acceptance> findByPresentationPresentationNo(String presentationNo);

    List<Acceptance> findByCreditIdOrderByAcceptedAtAsc(Long creditId);

    List<Acceptance> findByCreditIdAndStatusOrderByAcceptedAtAsc(Long creditId,
                                                                 AcceptanceStatus status);

    /** 某信用证版本下未撤销承兑的累计金额（该版本额度占用）。 */
    @Query("select coalesce(sum(a.amount), 0) from Acceptance a "
            + "where a.credit.id = :creditId and a.creditVersionNo = :versionNo "
            + "and a.status = com.chris64233.lettercredit.domain.AcceptanceStatus.ACCEPTED")
    BigDecimal sumAcceptedByCreditVersion(@Param("creditId") Long creditId,
                                          @Param("versionNo") int versionNo);
}
