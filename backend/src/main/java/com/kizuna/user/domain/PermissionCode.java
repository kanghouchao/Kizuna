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
 * <p>{@code defaultRoles} は既定ロールの行を<b>初めて作る時</b>に播種する授与で、省略は「どの既定ロールにも与えない」を意味する。既定ロールの権限は
 * 管理者が編集できるため、既存行の授与は起動時に取り直さない — ここを編集しても稼働中の DB の授権は変わらず、基線 seed と本宣言の一致は テストが機械検証する。
 */
@Getter
public enum PermissionCode {
  /** 受注の閲覧・登録・更新・状態遷移・削除（OrderController）。 */
  ORDER_MANAGE("受注の管理", PermissionGroup.BUSINESS, SystemRole.MANAGER, SystemRole.STAFF),

  /**
   * 完了した受注の内容訂正（明細行・実績時刻・コーススナップショット）。凍結済みの記録を動かす管理動作のため店長限定。
   *
   * <p>日常権限の {@code ORDER_MANAGE} で守らない。受注を扱う全員が持つ権限で仕切ると「権限のある利用者のみが訂正できる」が空文になる。
   *
   * <p>{@code ORDER_MANAGE} を<b>前提とする</b>。訂正の対象を探す一覧も、要求に載せる版を返す詳細も日常権限の側にあり、
   * この権限だけを与えた自作ロールは門へ到達できない（機構で強制する手立ては無く、付与時の約束である）。
   */
  ORDER_CORRECT("完了受注の訂正", PermissionGroup.BUSINESS, SystemRole.MANAGER),

  /** 受注と報酬の照会（読み取りのみ）。店舗集合を跨ぐ一覧・集計と、現在店舗の月次報酬・明細・PDF・訂正履歴・運営集計。 */
  ORDER_SET_VIEW(
      "受注・報酬の横断照会",
      PermissionGroup.BUSINESS,
      SystemRole.ADMIN,
      SystemRole.MANAGER,
      SystemRole.STAFF),

  /** 店舗サービス設定の閲覧・登録・更新・削除と変更履歴の照会。 */
  SERVICE_MANAGE("サービス設定の管理", PermissionGroup.BUSINESS, SystemRole.MANAGER),
  ADVERTISING_COST_VIEW("広告費の閲覧", PermissionGroup.BUSINESS, SystemRole.MANAGER),
  ADVERTISING_COST_MANAGE("広告費の登録・更新", PermissionGroup.BUSINESS, SystemRole.MANAGER),
  ADVERTISING_COST_EXPORT("広告費の出力", PermissionGroup.BUSINESS, SystemRole.MANAGER),
  ADVERTISING_COST_SET_VIEW("広告費の横断閲覧", PermissionGroup.BUSINESS, SystemRole.ADMIN),
  ADVERTISING_COST_SET_EXPORT("広告費の横断出力", PermissionGroup.BUSINESS, SystemRole.ADMIN),

  /** 授権範囲内の運営集計の閲覧。 */
  OPERATIONAL_REPORT_VIEW(
      "運営集計の閲覧", PermissionGroup.BUSINESS, SystemRole.ADMIN, SystemRole.MANAGER),

  /** 授権範囲内の運営集計の出力。閲覧権限と併用する。 */
  OPERATIONAL_REPORT_EXPORT(
      "運営集計の出力", PermissionGroup.BUSINESS, SystemRole.ADMIN, SystemRole.MANAGER),

  /** キャストの閲覧・登録・更新・削除（CastController — 在籍停止のキャストも対象）。 */
  CAST_MANAGE("キャストの管理", PermissionGroup.CAST, SystemRole.MANAGER, SystemRole.STAFF),

  /** キャスト招待の発行（確定系操作 — CastController の招待端点）。 */
  CAST_INVITE("キャストの招待", PermissionGroup.CAST, SystemRole.MANAGER),

  /** キャストカスタム項目定義の閲覧（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_VIEW("キャスト項目定義の閲覧", PermissionGroup.CAST, SystemRole.MANAGER, SystemRole.STAFF),

  /** キャストカスタム項目定義の登録・更新・削除（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_MANAGE("キャスト項目定義の管理", PermissionGroup.CAST, SystemRole.MANAGER),

  /** キャスト本人の検索と全店舗の在籍照会。担当店舗集合に依存しない全群の読み取り。 */
  CAST_PERSON_VIEW("キャスト在籍の横断照会", PermissionGroup.CAST, SystemRole.ADMIN),

  /** 出勤（シフト）の閲覧・登録・更新（ShiftController）。 */
  SHIFT_MANAGE("出勤の管理", PermissionGroup.CAST, SystemRole.MANAGER, SystemRole.STAFF),

  /** 応募者一覧・非公開詳細の閲覧。 */
  RECRUITMENT_VIEW("応募者の閲覧", PermissionGroup.CAST, SystemRole.MANAGER),

  /** 応募者情報と面接状態の登録・更新。 */
  RECRUITMENT_MANAGE("応募者の登録・更新", PermissionGroup.CAST, SystemRole.MANAGER),
  RECRUITMENT_DECIDE("採用判断の記録", PermissionGroup.CAST, SystemRole.MANAGER),

  /** 応募者の非公開添付の閲覧。 */
  RECRUITMENT_ATTACHMENT_VIEW("応募添付の閲覧", PermissionGroup.CAST, SystemRole.MANAGER),

  /** 応募者の非公開添付の登録・回復。 */
  RECRUITMENT_ATTACHMENT_MANAGE("応募添付の登録・回復", PermissionGroup.CAST, SystemRole.MANAGER),
  REMUNERATION_VIEW("報酬の閲覧", PermissionGroup.CAST, SystemRole.ADMIN, SystemRole.MANAGER),
  GUARANTEE_MANAGE("保証給の登録", PermissionGroup.CAST, SystemRole.MANAGER),
  BONUS_AWARD("ボーナスの付与", PermissionGroup.CAST, SystemRole.MANAGER),
  REMUNERATION_CORRECT("保証給・ボーナスの訂正", PermissionGroup.CAST, SystemRole.MANAGER),

  /** 顧客の閲覧・登録・更新・削除（CustomerController）。 */
  CUSTOMER_MANAGE("顧客の管理", PermissionGroup.CUSTOMER, SystemRole.MANAGER, SystemRole.STAFF),

  /**
   * 重複した顧客行の統合の実行（CustomerMergeController）。取り返しのつかない台帳級の操作のため店長限定。
   *
   * <p>統合には取消（undo）が無く、誤統合の修復は統合履歴を根拠とする人手作業になる（ADR 0010）。
   */
  CUSTOMER_MERGE("顧客の統合", PermissionGroup.CUSTOMER, SystemRole.MANAGER),

  /**
   * 会員の検索・照会（MemberConsoleController）。退会済みを含む全会員と、そのランク・残高・明細・関連を読む。
   *
   * <p>関連の展開は閲覧者の店舗集合に収まる分に限り、集合外は件数だけを見せる。店舗の顧客台帳の行へは店舗集合を跨いで到達させない。
   */
  MEMBER_VIEW("会員の照会", PermissionGroup.CUSTOMER, SystemRole.ADMIN),

  /**
   * 職員による会員の代行退会（MemberConsoleController）。本人の退会と同じ効果を操作者 = 職員で起こす不可逆の操作で、 {@link #MEMBER_VIEW}
   * を<b>前提とする</b> — 対象を探す一覧と確認する詳細は閲覧権限の側にある（機構では強制しない付与時の約束）。
   */
  MEMBER_WITHDRAW("会員の代行退会", PermissionGroup.CUSTOMER, SystemRole.ADMIN),

  /**
   * 会員ポイントの手動調整と、誤帰属の訂正（帰属記録の無効化と、その台帳訂正）。準金銭的な確定系操作のため店長限定。
   *
   * <p>無効化までこの権限に含めるのは、不可逆なその操作だけを店員に許すと、二段目の台帳訂正を実行できない者が訂正を 始められ、やり残しが人を跨いで残るためである（ADR 0012）。
   *
   * <p>受注を宛先とする操作（帰属の訂正・ポイントの巻き戻し）では {@code ORDER_MANAGE} を<b>前提とする</b>。宛先の受注を探す一覧も操作面が
   * 名乗る受注の現況も日常権限の側にあり、この権限だけを与えた自作ロールは操作面へ到達できない（機構で強制する手立ては無く、付与時の約束である）。
   */
  POINT_ADJUST("ポイントの手動調整・帰属訂正", PermissionGroup.CUSTOMER, SystemRole.MANAGER),
  REVIEW_VIEW("口コミの閲覧", PermissionGroup.CUSTOMER, SystemRole.MANAGER, SystemRole.STAFF),

  /** 口コミの手動受付・訂正・取り下げと公開許可の記録。 */
  REVIEW_MANAGE("口コミの受付・訂正", PermissionGroup.CUSTOMER, SystemRole.MANAGER, SystemRole.STAFF),

  /** 口コミの内部承認・却下。 */
  REVIEW_MODERATE("口コミの承認・却下", PermissionGroup.CUSTOMER, SystemRole.MANAGER),
  SURVEY_VIEW("アンケートの閲覧", PermissionGroup.CUSTOMER, SystemRole.MANAGER, SystemRole.STAFF),
  SURVEY_MANAGE("アンケートの作成・編集", PermissionGroup.CUSTOMER, SystemRole.MANAGER),
  SURVEY_RECORD("アンケート回答の記録", PermissionGroup.CUSTOMER, SystemRole.MANAGER, SystemRole.STAFF),

  /** 業務通知の内容・状態・履歴の閲覧。 */
  NOTIFICATION_VIEW("業務通知の閲覧", PermissionGroup.CUSTOMER, SystemRole.MANAGER, SystemRole.STAFF),

  /** 業務通知の作成。閲覧権限と併用する。 */
  NOTIFICATION_MANAGE("業務通知の作成", PermissionGroup.CUSTOMER, SystemRole.MANAGER),

  /** 業務通知の送信待ち登録と明示再試行。閲覧権限と併用する。 */
  NOTIFICATION_SEND("業務通知の送信・再試行", PermissionGroup.CUSTOMER, SystemRole.MANAGER),

  /**
   * 特典規則の閲覧・登録・更新・停用（BenefitRuleController）。
   *
   * <p>{@link #SYSTEM_CONFIG_MANAGE} へ同居させない。ポイントを発行する規則と SMTP ホストは監査上同類ではなく、
   * 共通設定を触れる者が黙って会員へポイントを配れる規則を建てられる形になるためである。
   *
   * <p>店舗集合で絞る規則を編めるには {@link #STORE_VIEW} と全店舗の作用域を<b>前提とする</b> — 適用店舗の選択肢は授権店舗一覧から
   * 引くので、これを欠く自作ロールでは選択肢が空になる（{@link #ORDER_CORRECT} と同じ、機構では強制しない付与時の約束）。
   */
  BENEFIT_MANAGE("特典規則の管理", PermissionGroup.CUSTOMER, SystemRole.ADMIN),

  /** 店舗（組織）の閲覧・登録・更新・削除（StoreController）。 */
  STORE_MANAGE("店舗の管理", PermissionGroup.ORGANIZATION, SystemRole.ADMIN),

  /** 授権店舗一覧の閲覧（StoreController）。 */
  STORE_VIEW(
      "担当店舗一覧の閲覧",
      PermissionGroup.ORGANIZATION,
      SystemRole.ADMIN,
      SystemRole.MANAGER,
      SystemRole.STAFF),

  /**
   * 従業員の閲覧・作成・授権変更（StaffController）。
   *
   * <p>委譲の守衛は「付与するロールの権限並集 ⊆ 行使者の有効権限並集」かつ「対象の店舗集合 ⊆ 行使者の店舗集合」のみ。 行使者自身が持たないものは配れず、持つものは配れる。
   */
  STAFF_MANAGE("従業員の作成・授権", PermissionGroup.ORGANIZATION, SystemRole.ADMIN, SystemRole.MANAGER),

  /**
   * 従業員アカウントの停止・再開・パスワード代理再設定（StaffController）。対象は行使者の店舗集合に収まる者に限る。
   *
   * <p>授権（ロール・店舗集合）は書かないので、授権を動かす {@link #STAFF_MANAGE} とは別の権限で仕切る。
   */
  STAFF_CREDENTIAL_MANAGE(
      "従業員の停止・再開・パスワード再設定", PermissionGroup.ORGANIZATION, SystemRole.ADMIN, SystemRole.MANAGER),

  /**
   * ロール定義の閲覧・登録・更新・削除と権限目録の参照（RoleController / PermissionController）。
   *
   * <p>ロールを定義できる者は権限の組合せを自由に作れるため、従業員の授権を委譲する {@link #STAFF_MANAGE} とは別の権限で仕切る。
   */
  ROLE_MANAGE("ロールの管理", PermissionGroup.ORGANIZATION, SystemRole.ADMIN),

  /**
   * サービスID（本人種別 SERVICE）の閲覧・作成・授権変更・停止・再開（ServiceIdentityController）。
   *
   * <p>作成が授権（ロール×店舗集合）を伴うため、授権を一切動かさない {@link #STAFF_CREDENTIAL_MANAGE} とは別の権限で仕切る。
   * 対話ログインできず全店バッチにもなりうる主体を、人のアカウントと別の鍵で管理する（ADR 0025）。
   *
   * <p>全店舗の作用域を<b>前提とする</b> — 店舗集合の選択肢は行使者自身の授権店舗一覧から引くため、担当を絞った
   * 自作ロールでは既存サービスIDの対象範囲を部分的にしか表示・編集できない（{@link #BENEFIT_MANAGE} と同じ、 機構では強制しない付与時の約束）。
   */
  SERVICE_ID_MANAGE("サービスIDの管理", PermissionGroup.ORGANIZATION, SystemRole.ADMIN),

  /** 店舗公開プロフィールの閲覧・更新・公開（StoreProfileController）。 */
  STORE_PROFILE_MANAGE(
      "店舗公開プロフィールの管理", PermissionGroup.OPERATION, SystemRole.MANAGER, SystemRole.STAFF),

  /** 共通設定の閲覧・更新（SystemConfigController）。 */
  SYSTEM_CONFIG_MANAGE("システム設定の管理", PermissionGroup.OPERATION, SystemRole.ADMIN),

  /** 登録済み処理の実行・再試行・中断記録と全体履歴の閲覧。 */
  TASK_MANAGE("処理の実行・履歴管理", PermissionGroup.OPERATION, SystemRole.ADMIN),

  /** サービス ID による実行基盤の検証処理。業務処理固有の権限を代替しない。サービス ID 専用のため既定授与はしない。 */
  TASK_EXECUTE("サービスIDによる処理実行", PermissionGroup.OPERATION),

  /** 全店舗の変更不可な業務監査の閲覧。 */
  AUDIT_VIEW("監査履歴の閲覧", PermissionGroup.OPERATION, SystemRole.ADMIN),

  /** 全会員の期限切れポイントの記帳。 */
  POINT_EXPIRE("期限切れポイントの記帳", PermissionGroup.OPERATION, SystemRole.ADMIN),

  /** 保持期間を過ぎた応募者の匿名化。取り消せない消去なので、既定授与はせず運用者とサービス ID へ明示授与する。 */
  APPLICANT_ANONYMIZE("保持期間を過ぎた応募者の匿名化", PermissionGroup.OPERATION),

  /** SERVICE による業務通知の配送と運営側からの手動実行。手動実行者として統括へ授与する。 */
  NOTIFICATION_DELIVER("業務通知の配送実行", PermissionGroup.OPERATION, SystemRole.ADMIN);

  /** 画面に出す日本語名。ロール編集の権限選択肢に使うため全権限で一意とする。 */
  private final String label;

  /** 権限選択肢の分け方。サイドバーの節と同じ業務領域で束ねる。 */
  private final PermissionGroup group;

  private final Set<SystemRole> defaultRoles;

  PermissionCode(String label, PermissionGroup group, SystemRole... defaultRoles) {
    this.label = label;
    this.group = group;
    this.defaultRoles = Set.of(defaultRoles);
  }

  /** SecurityContext 上の authority 表現（例: PERM_ORDER_MANAGE）を返す。 */
  public String authority() {
    return Authorities.permission(name());
  }
}
