package com.kizuna.advertising.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingMedium;
import java.time.OffsetDateTime;

public final class AdvertisingViews {
  private AdvertisingViews() {}

  /** 広告費の現在の状態。変更履歴の前後の値にはこの記録型の JSON をその時点の写しとして残し、読み戻すので、項目名と null の書き出しを変えない。媒体名は媒体の現在名。 */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CostView(
      String id,
      Long storeId,
      String month,
      AdvertisingCategory category,
      AdvertisingMedium medium,
      String agencyName,
      String planName,
      Integer inquiryCount,
      int amount,
      long version,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    static CostView of(AdvertisingCost c, String mediumName) {
      return new CostView(
          c.getId(),
          c.getStoreId(),
          c.getMonth(),
          c.getCategory(),
          new AdvertisingMedium(c.getMediumId(), mediumName),
          c.getAgencyName(),
          c.getPlanName(),
          c.getInquiryCount(),
          c.getAmount(),
          c.getVersion(),
          c.getCreatedAt(),
          c.getUpdatedAt());
    }
  }

  public record CopyResult(
      String id,
      Long storeId,
      String sourceMonth,
      String targetMonth,
      int copiedCount,
      long sourceVersion,
      long targetVersion,
      OffsetDateTime createdAt) {}

  public record ChangeDetail(AdvertisingChange change, CostView before, CostView after) {}
}
