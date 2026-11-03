package com.kizuna.remuneration.domain;

import com.kizuna.receipt.once.Fingerprint;
import java.time.LocalDate;
import java.util.UUID;

/** 保証条件とボーナスの書き込み命令。指紋は受領済みの行と照合が続くので、項目の並びと区切りを変えない。取消は両者で同じ形なので、取り消す対象の種類を指紋に含める。 */
public sealed interface RemunerationCommand {
  UUID requestId();

  String fingerprint();

  record CreateGuarantee(
      Long personId,
      LocalDate effectiveFrom,
      GuaranteeState state,
      Long dailyAmount,
      String reason,
      Long expectedVersion,
      UUID requestId)
      implements RemunerationCommand {
    public String fingerprint() {
      return Fingerprint.of(
          "GUARANTEE_CREATE", personId, effectiveFrom, state, dailyAmount, reason, expectedVersion);
    }
  }

  record CorrectGuarantee(
      String id,
      LocalDate effectiveFrom,
      GuaranteeState state,
      Long dailyAmount,
      String reason,
      String correctionReason,
      Long expectedVersion,
      UUID requestId)
      implements RemunerationCommand {
    public String fingerprint() {
      return Fingerprint.of(
          "GUARANTEE_CORRECT",
          id,
          effectiveFrom,
          state,
          dailyAmount,
          reason,
          correctionReason,
          expectedVersion);
    }
  }

  record CancelGuarantee(String id, String reason, Long expectedVersion, UUID requestId)
      implements RemunerationCommand {
    public String fingerprint() {
      return Fingerprint.of("GUARANTEE_CANCEL", id, reason, expectedVersion);
    }
  }

  record CreateBonus(Long personId, LocalDate awardDate, long amount, String reason, UUID requestId)
      implements RemunerationCommand {
    public String fingerprint() {
      return Fingerprint.of("BONUS_CREATE", personId, awardDate, amount, reason);
    }
  }

  record CorrectBonus(
      String id,
      LocalDate awardDate,
      long amount,
      String reason,
      String correctionReason,
      Long expectedVersion,
      UUID requestId)
      implements RemunerationCommand {
    public String fingerprint() {
      return Fingerprint.of(
          "BONUS_CORRECT", id, awardDate, amount, reason, correctionReason, expectedVersion);
    }
  }

  record CancelBonus(String id, String reason, Long expectedVersion, UUID requestId)
      implements RemunerationCommand {
    public String fingerprint() {
      return Fingerprint.of("BONUS_CANCEL", id, reason, expectedVersion);
    }
  }
}
