package com.kizuna.user.domain;

import lombok.Getter;

/**
 * 機能権限コードの目録。授権モデル「ロール × 担当店舗集合」の権限次元を表す。
 *
 * <p>権限は端点から静的に参照されるためデプロイと生命周期を共にするコード定義とし、DB の t_permissions
 * 行は本 enum の播種済み写像である（追加はコード変更と播種を伴う）。ロールは DB データとして自由に増減できる。
 *
 * <p>各権限の javadoc に操作単位（閲覧・登録・更新・確定・公開・出力 等）を明示する。SecurityContext への発行は {@link
 * Authorities#permission(String)}（{@code PERM_} 接頭辞）を経由する。
 */
@Getter
public enum PermissionCode {

  /** 店舗（組織）の閲覧・登録・更新・削除（PlatformStoreController）。 */
  STORE_MANAGE(Console.PLATFORM),

  /** 社内アカウント・権限の閲覧・付与・変更・停止とロールの管理（PlatformStaffController / RoleController）。 */
  STAFF_MANAGE(Console.PLATFORM),

  /** 共通設定の閲覧・更新（PlatformConfigController）。 */
  SYSTEM_CONFIG_MANAGE(Console.PLATFORM),

  /** プラットフォームコンソールメニューの標識権限。 */
  PLATFORM_MENU_VIEW(Console.PLATFORM),

  /** プラットフォーム共有領域への資産アップロード（登録・出力 — FileUploadController の platform 保存経路）。 */
  PLATFORM_ASSET_MANAGE(Console.PLATFORM),

  /** 授権店舗一覧の閲覧（PlatformStoreController）。 */
  STORE_VIEW(Console.SHARED),

  /** 授権店舗集合を跨ぐ受注の閲覧と、明示単一店舗指定での登録（PlatformOrderController）。 */
  ORDER_SET_MANAGE(Console.SHARED),

  /** 受注の閲覧・登録・更新・状態遷移・削除（OrderController）。 */
  ORDER_MANAGE(Console.STORE),

  /** 顧客の閲覧・登録・更新・削除（CustomerController）。 */
  CUSTOMER_MANAGE(Console.STORE),

  /** 出勤（シフト）の閲覧・登録・更新（ShiftController）。 */
  SHIFT_MANAGE(Console.STORE),

  /** 在籍キャストの閲覧・登録・更新・削除（CastController）。 */
  CAST_MANAGE(Console.STORE),

  /** キャスト招待の発行（確定系操作 — CastController の招待端点）。 */
  CAST_INVITE(Console.STORE),

  /** キャストカスタム項目定義の閲覧（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_VIEW(Console.STORE),

  /** キャストカスタム項目定義の登録・更新・削除（CastFieldDefinitionController）。 */
  CAST_FIELD_DEF_MANAGE(Console.STORE),

  /** 店舗公開プロフィールの閲覧・更新・公開（StoreProfileController）。 */
  STORE_PROFILE_MANAGE(Console.STORE),

  /** 店舗コンソールメニューの標識権限。 */
  STORE_MENU_VIEW(Console.STORE);

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

  PermissionCode(Console console) {
    this.console = console;
  }

  /** SecurityContext 上の authority 表現（例: PERM_ORDER_MANAGE）を返す。 */
  public String authority() {
    return Authorities.permission(name());
  }
}
