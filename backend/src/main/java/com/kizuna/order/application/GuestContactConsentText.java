package com.kizuna.order.application;

/** ゲスト申請で取る連絡先同意の現行文面。 */
public record GuestContactConsentText(String version, String businessText, String marketingText) {
  public static GuestContactConsentText current() {
    return new GuestContactConsentText(
        "1",
        "入力した連絡先を、この予約申請と成立した予約に関する確認・変更などの連絡に使用することを許可します。",
        "入力した連絡先を、この店舗のキャンペーンやサービスの案内に使用することを許可します（任意）。");
  }
}
