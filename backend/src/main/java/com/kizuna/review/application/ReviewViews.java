package com.kizuna.review.application;

import com.kizuna.receipt.once.Receipt;
import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewPermissionView;
import com.kizuna.review.domain.ReviewSummaryView;
import java.time.LocalDate;

/** 口コミの読み口の結果。掲載中か・受領日は読んだ時点の時計と業務の時間帯で application が決める。 */
public final class ReviewViews {
  private ReviewViews() {}

  /** 公開許可の記録は無ければ null。 */
  public record Detail(
      ReviewDetailsView review, ReviewPermissionView permission, boolean displayed) {}

  public record Summary(ReviewSummaryView review, boolean displayed) {}

  public record PublicEntry(String displayName, String body, LocalDate receivedOn) {}

  /** 口コミは現在の状態、操作の種別・対象・成立時の版は受領の行から読む。 */
  public record Written(Detail review, Receipt receipt) {}
}
