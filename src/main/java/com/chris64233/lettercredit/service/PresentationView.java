package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.PresentationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 交单视图，含所依据的信用证版本号与全部审核版本（旧版本保留）。
 */
public record PresentationView(Long id,
                               String presentationNo,
                               String creditNo,
                               int creditVersionNo,
                               BigDecimal amount,
                               String currency,
                               LocalDate presentationDate,
                               PresentationStatus status,
                               int latestVersionNo,
                               List<ReviewVersionView> versions) {
}
