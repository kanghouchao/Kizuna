package com.kizuna.user.domain;

/** プラットフォームユーザーの本人種別。ロールで授権されるのは STAFF と SERVICE で、CAST / MEMBER は権限モデルに入らない独立した本人種別として扱う。 */
public enum UserType {
  /** 社内利用者。ロール × 担当店舗集合で授権される。 */
  STAFF,
  /** サービスID。定期処理・外部連携の実行主体で、自前の資格情報を持たず対話ログインしない。授権は STAFF と同じ「ロール × 店舗集合」。 */
  SERVICE,
  /** キャスト本人。店舗招待制で紐づき、本人ポータルのみ利用する。 */
  CAST,
  /** 会員本人。本人のデータのみ利用する。 */
  MEMBER;

  /**
   * ロールで授権される本人種別か。
   *
   * <p>授権の述語（ロール授与の不変条件・authority の発行）専用であり、人のスタッフ管理面の対象判定には使えない — あちらは SERVICE を含めない STAFF
   * 限定である（サービスIDはスタッフのアカウント管理面に現れない）。
   */
  public boolean holdsRoles() {
    return this == STAFF || this == SERVICE;
  }
}
