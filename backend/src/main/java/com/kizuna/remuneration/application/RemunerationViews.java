package com.kizuna.remuneration.application;

import com.kizuna.remuneration.domain.BonusAward;
import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import tools.jackson.databind.JsonNode;

/** 保証条件・ボーナスの現在の状態。変更履歴の前後の値にはこの記録型の JSON をその時点の写しとして残すので、項目名を変えない。 */
public final class RemunerationViews {
  private RemunerationViews() {}

  public record GuaranteeView(
      String id,
      Long personId,
      LocalDate effectiveFrom,
      GuaranteeState state,
      Long dailyAmount,
      String reason,
      OffsetDateTime cancelledAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    static GuaranteeView of(GuaranteeTerm t) {
      return new GuaranteeView(
          t.getId(),
          t.getPersonId(),
          t.getEffectiveFrom(),
          t.getState(),
          t.getDailyAmount(),
          t.getReason(),
          utc(t.getCancelledAt()),
          utc(t.getCreatedAt()),
          utc(t.getUpdatedAt()));
    }
  }

  public record BonusView(
      String id,
      Long personId,
      LocalDate awardDate,
      long amount,
      long effectiveAmount,
      String reason,
      long version,
      OffsetDateTime cancelledAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    static BonusView of(BonusAward a) {
      return new BonusView(
          a.getId(),
          a.getPersonId(),
          a.getAwardDate(),
          a.getAmount(),
          a.effectiveAmount(),
          a.getReason(),
          a.getVersion(),
          utc(a.getCancelledAt()),
          utc(a.getCreatedAt()),
          utc(a.getUpdatedAt()));
    }
  }

  /** version は時間線の版。 */
  public record GuaranteeList(long version, Page<GuaranteeView> entries) {}

  /** version は成立時の時間線の版。 */
  public record GuaranteeMutation(GuaranteeView guarantee, long version, String changeId) {}

  public record BonusMutation(BonusView bonus, String changeId) {}

  public record ChangeView(
      String id,
      Long actorId,
      String action,
      String reason,
      JsonNode beforeValue,
      JsonNode afterValue,
      OffsetDateTime createdAt) {}

  private static OffsetDateTime utc(OffsetDateTime value) {
    return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
