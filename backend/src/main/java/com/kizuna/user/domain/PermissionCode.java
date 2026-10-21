package com.kizuna.user.domain;

import com.kizuna.shared.security.Authorities;
import java.util.Set;
import lombok.Getter;

/**
 * 機能権限コードの目録。授権モデル「ロール × 担当店舗集合」の権限次元を表す。平台・店舗といった「側」の分類は持たない —
 * 何ができるかは権限、どの店舗に対してかは本人の店舗集合がそれぞれ独立に決める。
 *
 * <p>権限は端点から静的に参照されるためデプロイと生命周期を共にするコード定義とし、DB の t_permissions 行は本 enum の宣言から播種が導出する。 利用者自作のロールは DB
 * データとして自由に増減できる。
 *
 * <p>各権限の javadoc に操作単位（閲覧・登録・更新・確定・公開・出力 等）を明示する。SecurityContext への発行は {@link
 * Authorities#permission(String)}（{@code PERM_} 接頭辞）を経由する。
 *
 * <p>{@code defaultRoles} は既定ロールの行を<b>初めて作る時</b>に播種する授与で、省略は「どの既定ロールにも与えない」を意味する。グループ管理者は サービス ID
 * 専用の TASK_EXECUTE を除く全権限を初期授与として持つ — 委譲の守衛が「行使者の権限の部分集合」なので、根となる管理者が
 * 全権限を持たないと店長や店舗スタッフを誰も作れない。既定ロールの権限は 管理者が編集できるため、既存行の授与は起動時に取り直さない — ここを編集しても稼働中の DB の授権は変わらず、基線
 * seed と本宣言の一致は テストが機械検証する。
 */
@Getter
public enum PermissionCode {
  ADVERTISING_COST_VIEW(SystemRole.STORE_MANAGER),
  ADVERTISING_COST_MANAGE(SystemRole.STORE_MANAGER),
  ADVERTISING_COST_EXPORT(SystemRole.STORE_MANAGER),
  ADVERTISING_COST_SET_VIEW(SystemRole.HQ_ADMIN),
  ADVERTISING_COST_SET_EXPORT(SystemRole.HQ_ADMIN),
  /** 口コミの閲覧。 */
  REVIEW_VIEW(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),
  SURVEY_VIEW(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),
  SURVEY_MANAGE(SystemRole.STORE_MANAGER),
  SURVEY_RECORD(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),
  /** 口コミの手動受付・訂正・取り下げと公開許可の記録。 */
  REVIEW_MANAGE(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),
  /** 口コミの内部承認・却下。 */
  REVIEW_MODERATE(SystemRole.STORE_MANAGER),
  /** 業務通知の内容・状態・履歴の閲覧。 */
  NOTIFICATION_VIEW(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),
  /** 業務通知の作成。閲覧権限と併用する。 */
  NOTIFICATION_MANAGE(SystemRole.STORE_MANAGER),
  /** 業務通知の送信待ち登録と明示再試行。閲覧権限と併用する。 */
  NOTIFICATION_SEND(SystemRole.STORE_MANAGER),
  /** SERVICE による業務通知の配送と運営側からの手動実行。手動実行者として HQ へ授与する。 */
  NOTIFICATION_DELIVER(SystemRole.HQ_ADMIN),

  /** 授権範囲内の運営集計の閲覧。 */
  OPERATIONAL_REPORT_VIEW(SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER),

  /** 授権範囲内の運営集計の出力。閲覧権限と併用する。 */
  OPERATIONAL_REPORT_EXPORT(SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER),

  /** 登録済み処理の実行・再試行・中断記録と全体履歴の閲覧。 */
  TASK_MANAGE(SystemRole.HQ_ADMIN),

  /** サービス ID による実行基盤の検証処理。業務処理固有の権限を代替しない。サービス ID 専用のため既定授与はしない。 */
  TASK_EXECUTE(),

  /** 全会員の期限切れポイントの記帳。 */
  POINT_EXPIRE(SystemRole.HQ_ADMIN),

  /** 全店舗の変更不可な業務監査の閲覧。 */
  AUDIT_VIEW(SystemRole.HQ_ADMIN),

  /** 応募者一覧・非公開詳細の閲覧。 */
  RECRUITMENT_VIEW(SystemRole.STORE_MANAGER),

  /** 応募者情報と面接状態の登録・更新。 */
  RECRUITMENT_MANAGE(SystemRole.STORE_MANAGER),

  /** 採用判断の記録。 */
  RECRUITMENT_DECIDE(SystemRole.STORE_MANAGER),

  /** 応募者の非公開添付の閲覧。 */
  RECRUITMENT_ATTACHMENT_VIEW(SystemRole.STORE_MANAGER),

  /** 応募者の非公開添付の登録・回復。 */
  RECRUITMENT_ATTACHMENT_MANAGE(SystemRole.STORE_MANAGER),

  /** 店舗（組織）の閲覧・登録・更新・削除（PlatformStoreController）。 */
  STORE_MANAGE(SystemRole.HQ_ADMIN),

  /** キャスト本人の検索と全店舗の在籍照会。担当店舗集合に依存しない全群の読み取り。 */
  CAST_PERSON_VIEW(SystemRole.HQ_ADMIN),

  /**
   * ロール定義の閲覧・登録・更新・削除と権限目録の参照（RoleController / PermissionController）。
   *
   * <p>ロールを定義できる者は権限の組合せを自由に作れるため、従業員の授権を委譲する {@link #STORE_STAFF_MANAGE} とは別の権限で仕切る。
   */
  ROLE_MANAGE(SystemRole.HQ_ADMIN),

  /**
   * 従業員アカウントの停止・再開・パスワード代理再設定（PlatformStaffController）。対象は行使者の店舗集合に収まる者に限る。
   *
   * <p>授権（ロール・店舗集合）は書かないので、授権を動かす {@link #STORE_STAFF_MANAGE} とは別の権限で仕切る。
   */
  STAFF_ACCOUNT_MANAGE(SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER),

  /** 共通設定の閲覧・更新（PlatformConfigController）。 */
  SYSTEM_CONFIG_MANAGE(SystemRole.HQ_ADMIN),

  /** 全店共有領域への資産アップロード（登録・出力 — FileUploadController の店舗文脈を持たない保存経路）。 */
  PLATFORM_ASSET_MANAGE(SystemRole.HQ_ADMIN),

  /**
   * 特典規則の閲覧・登録・更新・停用（PlatformBenefitRuleController）。
   *
   * <p>{@link #SYSTEM_CONFIG_MANAGE} へ同居させない。ポイントを発行する規則と SMTP ホストは監査上同類ではなく、
   * 共通設定を触れる者が黙って会員へポイントを配れる規則を建てられる形になるためである。
   *
   * <p>店舗集合で絞る規則を編めるには {@link #STORE_VIEW} と全店舗の作用域を<b>前提とする</b> — 適用店舗の選択肢は授権店舗一覧から
   * 引くので、これを欠く自作ロールでは選択肢が空になる（{@link #ORDER_CORRECT} と同じ、機構では強制しない付与時の約束）。
   */
  BENEFIT_MANAGE(SystemRole.HQ_ADMIN),

  /**
   * サービスID（本人種別 SERVICE）の閲覧・作成・授権変更・停止・再開（ServiceIdentityController）。
   *
   * <p>作成が授権（ロール×店舗集合）を伴うため、授権を一切動かさない {@link #STAFF_ACCOUNT_MANAGE} とは別の権限で仕切る。
   * 対話ログインできず全店バッチにもなりうる主体を、人のアカウントと別の鍵で管理する（ADR 0025）。
   *
   * <p>全店舗の作用域を<b>前提とする</b> — 店舗集合の選択肢は行使者自身の授権店舗一覧から引くため、担当を絞った
   * 自作ロールでは既存サービスIDの対象範囲を部分的にしか表示・編集できない（{@link #BENEFIT_MANAGE} と同じ、 機構では強制しない付与時の約束）。
   */
  SERVICE_ID_MANAGE(SystemRole.HQ_ADMIN),

  /** 授権店舗一覧の閲覧（PlatformStoreController）。 */
  STORE_VIEW(SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** 授権店舗集合を跨ぐ受注の閲覧と、明示単一店舗指定での登録（PlatformOrderController）。 */
  ORDER_SET_MANAGE(SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /**
   * 従業員の閲覧・作成・授権変更（PlatformStaffController）。
   *
   * <p>委譲の守衛は「付与するロールの権限並集 ⊆ 行使者の有効権限並集」かつ「対象の店舗集合 ⊆ 行使者の店舗集合」のみ。 行使者自身が持たないものは配れず、持つものは配れる。
   */
  STORE_STAFF_MANAGE(SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER),

  /** 受注の閲覧・登録・更新・状態遷移・削除（OrderController）。 */
  ORDER_MANAGE(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** 店舗サービス設定の閲覧・登録・更新・削除と変更履歴の照会。 */
  SERVICE_MANAGE(SystemRole.STORE_MANAGER),

  /** 顧客の閲覧・登録・更新・削除（CustomerController）。 */
  CUSTOMER_MANAGE(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /**
   * 重複した顧客行の統合の実行（CustomerMergeController）。取り返しのつかない台帳級の操作のため店長限定。
   *
   * <p>統合には取消（undo）が無く、誤統合の修復は統合履歴を根拠とする人手作業になる（ADR 0010）。
   */
  CUSTOMER_MERGE(SystemRole.STORE_MANAGER),

  /**
   * 完了した受注の内容訂正（明細行・実績時刻・コーススナップショット）。凍結済みの記録を動かす管理動作のため店長限定。
   *
   * <p>日常権限の {@code ORDER_MANAGE} で守らない。受注を扱う全員が持つ権限で仕切ると「権限のある利用者のみが訂正できる」が空文になる。
   *
   * <p>{@code ORDER_MANAGE} を<b>前提とする</b>。訂正の対象を探す一覧も、要求に載せる版を返す詳細も日常権限の側にあり、
   * この権限だけを与えた自作ロールは門へ到達できない（機構で強制する手立ては無く、付与時の約束である）。
   */
  ORDER_CORRECT(SystemRole.STORE_MANAGER),

  /**
   * 会員ポイントの手動調整と、誤帰属の訂正（帰属記録の無効化と、その台帳訂正）。準金銭的な確定系操作のため店長限定。
   *
   * <p>無効化までこの権限に含めるのは、不可逆なその操作だけを店員に許すと、二段目の台帳訂正を実行できない者が訂正を 始められ、やり残しが人を跨いで残るためである（ADR 0012）。
   *
   * <p>受注を宛先とする操作（帰属の訂正・ポイントの巻き戻し）では {@code ORDER_MANAGE} を<b>前提とする</b>。宛先の受注を探す一覧も操作面が
   * 名乗る受注の現況も日常権限の側にあり、この権限だけを与えた自作ロールは操作面へ到達できない（機構で強制する手立ては無く、付与時の約束である）。
   */
  POINT_ADJUST(SystemRole.STORE_MANAGER),

  /** 出勤（シフト）の閲覧・登録・更新（ShiftController）。 */
  SHIFT_MANAGE(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  REMUNERATION_VIEW(SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER),
  GUARANTEE_MANAGE(SystemRole.STORE_MANAGER),
  BONUS_AWARD(SystemRole.STORE_MANAGER),
  REMUNERATION_CORRECT(SystemRole.STORE_MANAGER),

  /** キャストの閲覧・登録・更新・削除（CastController — 在籍停止のキャストも対象）。 */
  CAST_MANAGE(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** キャスト招待の発行（確定系操作 — CastController の招待端点）。 */
  CAST_INVITE(SystemRole.STORE_MANAGER),

  /** キャストカスタム項目定義の閲覧（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_VIEW(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** キャストカスタム項目定義の登録・更新・削除（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_MANAGE(SystemRole.STORE_MANAGER),

  /** 店舗公開プロフィールの閲覧・更新・公開（StoreProfileController）。 */
  STORE_PROFILE_MANAGE(SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF);

  private final Set<SystemRole> defaultRoles;

  PermissionCode(SystemRole... defaultRoles) {
    this.defaultRoles = Set.of(defaultRoles);
  }

  /** SecurityContext 上の authority 表現（例: PERM_ORDER_MANAGE）を返す。 */
  public String authority() {
    return Authorities.permission(name());
  }
}
