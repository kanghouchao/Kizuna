package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.shared.exception.ServiceException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class RemunerationRequests {
  private RemunerationRequests() {}

  public record GuaranteeCreateRequest(
      @NotNull @Positive Long personId,
      @NotNull LocalDate effectiveFrom,
      @NotNull GuaranteeState state,
      Long dailyAmount,
      @NotBlank @Size(max = 500) String reason,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull UUID requestId) {
    public GuaranteeCreateRequest {
      reason = reason == null ? null : reason.strip();
      if (effectiveFrom != null && (effectiveFrom.getYear() < 1 || effectiveFrom.getYear() > 9999))
        throw new ServiceException("日付は西暦1年から9999年の範囲で指定してください");
    }
  }

  public record GuaranteeCorrectionRequest(
      @NotNull LocalDate effectiveFrom,
      @NotNull GuaranteeState state,
      Long dailyAmount,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 500) String correctionReason,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull UUID requestId) {
    public GuaranteeCorrectionRequest {
      reason = reason == null ? null : reason.strip();
      correctionReason = correctionReason == null ? null : correctionReason.strip();
      if (effectiveFrom != null && (effectiveFrom.getYear() < 1 || effectiveFrom.getYear() > 9999))
        throw new ServiceException("日付は西暦1年から9999年の範囲で指定してください");
    }
  }

  public record BonusCreateRequest(
      @NotNull @Positive Long personId,
      @NotNull LocalDate awardDate,
      @Positive long amount,
      @NotBlank @Size(max = 500) String reason,
      @NotNull UUID requestId) {
    public BonusCreateRequest {
      reason = reason == null ? null : reason.strip();
      if (awardDate != null && (awardDate.getYear() < 1 || awardDate.getYear() > 9999))
        throw new ServiceException("日付は西暦1年から9999年の範囲で指定してください");
    }
  }

  public record BonusCorrectionRequest(
      @NotNull LocalDate awardDate,
      @Positive long amount,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 500) String correctionReason,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull UUID requestId) {
    public BonusCorrectionRequest {
      reason = reason == null ? null : reason.strip();
      correctionReason = correctionReason == null ? null : correctionReason.strip();
      if (awardDate != null && (awardDate.getYear() < 1 || awardDate.getYear() > 9999))
        throw new ServiceException("日付は西暦1年から9999年の範囲で指定してください");
    }
  }

  public record CancellationRequest(
      @NotBlank @Size(max = 500) String reason,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull UUID requestId) {
    public CancellationRequest {
      reason = reason == null ? null : reason.strip();
    }
  }
}
