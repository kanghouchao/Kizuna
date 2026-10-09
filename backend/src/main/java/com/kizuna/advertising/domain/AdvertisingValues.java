package com.kizuna.advertising.domain;

import com.kizuna.shared.exception.ServiceException;

public record AdvertisingValues(
    AdvertisingCategory category,
    String mediaName,
    String agencyName,
    String planName,
    Integer inquiryCount,
    Integer amount) {
  public AdvertisingValues {
    mediaName = text(mediaName, 200);
    agencyName = text(agencyName, 200);
    planName = text(planName, 200);
    if (category == null
        || mediaName == null
        || amount == null
        || amount < 0
        || (inquiryCount != null && inquiryCount < 0))
      throw new ServiceException("区分・媒体・非負の金額と問い合わせ人数を入力してください");
  }

  public static String text(String value, int max) {
    if (value == null || value.isBlank()) return null;
    String clean = value.strip();
    if (clean.length() > max || clean.indexOf('\0') >= 0)
      throw new ServiceException("入力文字列の長さまたは内容を確認してください");
    return clean;
  }

  public static String reason(String value) {
    var clean = text(value, 500);
    if (clean == null) throw new ServiceException("変更理由を入力してください");
    return clean;
  }

  public AdvertisingValues copied() {
    return new AdvertisingValues(category, mediaName, agencyName, planName, null, amount);
  }
}
