package com.kizuna.user.domain;

import java.util.Set;
import lombok.Getter;

/**
 * 機能権限コードの目録。授権モデル「ロール × 担当店舗集合」の権限次元を表す。
 *
 * <p>権限は端点から静的に参照されるためデプロイと生命周期を共にするコード定義とし、DB の t_permissions 行と平台既定ロールへの授与は本 enum
 * の宣言から播種が導出する（追加はここへ成員を足すだけで足り、目録行や既定授与の手書きは要らない）。利用者自作のロールは DB データとして自由に増減できる。
 *
 * <p>各権限の javadoc に操作単位（閲覧・登録・更新・確定・公開・出力 等）を明示する。SecurityContext への発行は {@link
 * Authorities#permission(String)}（{@code PERM_} 接頭辞）を経由する。
 *
 * <p>{@code defaultRoles} は既定で授与する平台既定ロールの列挙で、省略は「どの既定ロールにも与えない」を意味する — 新しい権限が黙って既存ロールへ流れ込まないための
 * fail-closed である。既定ロールの授与は宣言の写像として毎回取り直されるため、取り下げもここを編集するだけで既存 DB に反映される（移行 changeset は要らない）。
 */
@Getter
public enum PermissionCode {

  /** 店舗（組織）の閲覧・登録・更新・削除（PlatformStoreController）。 */
  STORE_MANAGE(Console.PLATFORM, SystemRole.HQ_ADMIN),

  /** 社内アカウント・権限の閲覧・付与・変更・停止とロールの管理（PlatformStaffController / RoleController）。 */
  STAFF_MANAGE(Console.PLATFORM, SystemRole.HQ_ADMIN),

  /** 共通設定の閲覧・更新（PlatformConfigController）。 */
  SYSTEM_CONFIG_MANAGE(Console.PLATFORM, SystemRole.HQ_ADMIN),

  /** プラットフォームコンソールメニューの標識権限。 */
  PLATFORM_MENU_VIEW(Console.PLATFORM, SystemRole.HQ_ADMIN),

  /** プラットフォーム共有領域への資産アップロード（登録・出力 — FileUploadController の platform 保存経路）。 */
  PLATFORM_ASSET_MANAGE(Console.PLATFORM, SystemRole.HQ_ADMIN),

  /** 授権店舗一覧の閲覧（PlatformStoreController）。 */
  STORE_VIEW(Console.SHARED, SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** 授権店舗集合を跨ぐ受注の閲覧と、明示単一店舗指定での登録（PlatformOrderController）。 */
  ORDER_SET_MANAGE(
      Console.SHARED, SystemRole.HQ_ADMIN, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** 受注の閲覧・登録・更新・状態遷移・削除（OrderController）。 */
  ORDER_MANAGE(Console.STORE, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** 顧客の閲覧・登録・更新・削除（CustomerController）。 */
  CUSTOMER_MANAGE(Console.STORE, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /**
   * 重複した顧客行の統合の実行（CustomerMergeController）。取り返しのつかない台帳級の操作のため店長限定。
   *
   * <p>統合には取消（undo）が無く、誤統合の修復は統合履歴を根拠とする人手作業になる（ADR 0010）。
   */
  CUSTOMER_MERGE(Console.STORE, SystemRole.STORE_MANAGER),

  /**
   * 完了した受注の内容訂正（明細行・実績時刻・コーススナップショット）。凍結済みの記録を動かす管理動作のため店長限定。
   *
   * <p>日常権限の {@code ORDER_MANAGE} で守らない。受注を扱う全員が持つ権限で仕切ると「権限のある利用者のみが訂正できる」が空文になる。
   */
  ORDER_CORRECT(Console.STORE, SystemRole.STORE_MANAGER),

  /**
   * 会員ポイントの手動調整と、誤帰属の訂正（帰属記録の無効化と、その台帳訂正）。準金銭的な確定系操作のため店長限定。
   *
   * <p>無効化までこの権限に含めるのは、不可逆なその操作だけを店員に許すと、二段目の台帳訂正を実行できない者が訂正を 始められ、やり残しが人を跨いで残るためである（ADR 0012）。
   */
  POINT_ADJUST(Console.STORE, SystemRole.STORE_MANAGER),

  /** 出勤（シフト）の閲覧・登録・更新（ShiftController）。 */
  SHIFT_MANAGE(Console.STORE, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** キャストの閲覧・登録・更新・削除（CastController — 在籍停止のキャストも対象）。 */
  CAST_MANAGE(Console.STORE, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** キャスト招待の発行（確定系操作 — CastController の招待端点）。 */
  CAST_INVITE(Console.STORE, SystemRole.STORE_MANAGER),

  /** キャストカスタム項目定義の閲覧（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_VIEW(Console.STORE, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** キャストカスタム項目定義の登録・更新・削除（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_MANAGE(Console.STORE, SystemRole.STORE_MANAGER),

  /** 店舗公開プロフィールの閲覧・更新・公開（StoreProfileController）。 */
  STORE_PROFILE_MANAGE(Console.STORE, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF),

  /** 店舗コンソールメニューの標識権限。 */
  STORE_MENU_VIEW(Console.STORE, SystemRole.STORE_MANAGER, SystemRole.STORE_STAFF);

  /** 権限が属するコンソール。ログイン後の着地先導出と店舗文脈ブリッジ（storeBridge claim）の判定に用いる。 */
  public enum Console {
    /** プラットフォーム（HQ）コンソール専用。 */
    PLATFORM,
    /** 店舗コンソール専用。保持者は店舗文脈（X-Store-ID）を確立できる。 */
    STORE,
    /** プラットフォーム・店舗の両方から利用する跨店参照系。 */
    SHARED
  }

  private final Console console;

  private final Set<SystemRole> defaultRoles;

  PermissionCode(Console console, SystemRole... defaultRoles) {
    this.console = console;
    this.defaultRoles = Set.of(defaultRoles);
  }

  /** SecurityContext 上の authority 表現（例: PERM_ORDER_MANAGE）を返す。 */
  public String authority() {
    return Authorities.permission(name());
  }
}
