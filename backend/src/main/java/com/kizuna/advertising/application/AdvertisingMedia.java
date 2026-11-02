package com.kizuna.advertising.application;

import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.medium.lookup.MediumReference;
import java.util.Map;

/** 受注・入店が持つ媒体 ID を、引いた媒体の現在名で集計用の媒体へ写す。 */
final class AdvertisingMedia {
  private AdvertisingMedia() {}

  // 複合 FK が同一店舗の媒体の実在を保証するので、媒体 ID を持つのに引けないのは不変条件の破れ。
  static AdvertisingMedium of(Map<String, MediumReference> media, String mediumId) {
    if (mediumId == null) return null;
    var medium = media.get(mediumId);
    if (medium == null) throw new IllegalStateException("記録の媒体が見つかりません");
    return new AdvertisingMedium(medium.id(), medium.name());
  }
}
