package com.kizuna.advertising.domain;

import java.util.Comparator;

/** 集計・出力の媒体。まとまりは id で決まり、name は表示用の現在名。 */
public record AdvertisingMedium(String id, String name) {
  public static final Comparator<AdvertisingMedium> BY_NAME_THEN_ID =
      Comparator.comparing(AdvertisingMedium::name).thenComparing(AdvertisingMedium::id);
}
