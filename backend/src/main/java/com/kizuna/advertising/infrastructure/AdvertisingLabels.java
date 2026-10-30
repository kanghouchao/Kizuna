package com.kizuna.advertising.infrastructure;

import com.kizuna.advertising.domain.AdvertisingCategory;

/** 出力のセル値。画面の表示ラベルと同じ文言にする。 */
final class AdvertisingLabels {
  static final String CRITERIA = "条件と総計";
  static final String EXACT_NAME = "保存媒体名の完全一致";

  private AdvertisingLabels() {}

  static String category(AdvertisingCategory category) {
    return switch (category) {
      case SALES -> "営業広告";
      case RECRUITMENT -> "採用広告";
    };
  }
}
