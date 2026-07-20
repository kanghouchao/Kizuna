package com.kizuna.user.domain;

import lombok.Getter;

/**
 * 能力（機能権限）の目録。授権モデル「能力 × 担当店舗集合 ×（必要時）精算範囲」の能力次元を表す（#382 / #398）。
 *
 * <p>能力は端点から静的に参照されるため部署（デプロイ）と生命周期を共にするコード定義とする。「固定ロール一覧を作らない」裁定が禁じるのは束（ロール）の固定であり、
 * 能力目録は既存端点に対応する範囲のみ定義する — 未実装機能（会計確定・全件出力・広告費等）の能力は対応する票（#386〜#389 等）で追加する。
 *
 * <p>各能力の javadoc に操作単位（閲覧・登録・更新・確定・公開・出力 等）を明示する（#382 要件 2）。SecurityContext への発行は {@link
 * Authorities#permission(String)}（{@code PERM_} 接頭辞）を経由する。
 */
@Getter
public enum Capability {

  /** 店舗（組織）の閲覧・登録・更新・削除（PlatformStoreController）。 */
  STORE_MANAGE(Console.PLATFORM),

  /** 社内アカウント・権限の閲覧・付与・変更・停止と付与履歴の閲覧（PlatformStaffController / CapabilityBundleController）。 */
  STAFF_MANAGE(Console.PLATFORM),

  /** 共通設定の閲覧・更新（PlatformConfigController）。 */
  SYSTEM_CONFIG_MANAGE(Console.PLATFORM),

  /** プラットフォームコンソールメニューの標識能力。 */
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

  /** 店舗コンソールメニューの標識能力。 */
  STORE_MENU_VIEW(Console.STORE);

  /** 能力が属するコンソール。ログイン後の着地先導出と店舗文脈ブリッジ（storeBridge claim）の判定に用いる。 */
  public enum Console {
    /** プラットフォーム（HQ）コンソール専用。 */
    PLATFORM,
    /** 店舗コンソール専用。保持者は店舗文脈（X-Store-ID）を確立できる。 */
    STORE,
    /** プラットフォーム・店舗の両方から利用する跨店参照系。 */
    SHARED
  }

  private final Console console;

  Capability(Console console) {
    this.console = console;
  }

  /** SecurityContext 上の authority 表現（例: PERM_ORDER_MANAGE）を返す。 */
  public String authority() {
    return Authorities.permission(name());
  }
}
