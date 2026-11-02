package com.kizuna.advertising.infrastructure;

import com.kizuna.advertising.domain.AdvertisingCategory;

/** 出力のセル値。画面の表示ラベルと同じ文言にする。 */
final class AdvertisingLabels {
  static final String CRITERIA = "条件と総計";
  static final String MEDIUM_ID_MATCHING = "媒体 ID で照合（表示は現在の媒体名）";

  private AdvertisingLabels() {}

  static String category(AdvertisingCategory category) {
    return switch (category) {
      case SALES -> "営業広告";
      case RECRUITMENT -> "採用広告";
    };
  }
}
