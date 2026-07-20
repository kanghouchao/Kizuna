// パスワード変更リクエスト
export interface PasswordChangeRequest {
  current_password: string;
  new_password: string;
}

// 認証レスポンス
export interface LoginResponse {
  token: string;
  expires_at: number;
}

// 本人種別（バックエンド user/domain/UserType.java と対応。#398 で固定ロールは廃止）
export type PlatformUserType = 'STAFF' | 'CAST' | 'MEMBER';

// 能力（バックエンド user/domain/Capability.java と対応）
export type PlatformCapability =
  | 'STORE_MANAGE'
  | 'STAFF_MANAGE'
  | 'SYSTEM_CONFIG_MANAGE'
  | 'PLATFORM_MENU_VIEW'
  | 'PLATFORM_ASSET_MANAGE'
  | 'STORE_VIEW'
  | 'ORDER_SET_MANAGE'
  | 'ORDER_MANAGE'
  | 'CUSTOMER_MANAGE'
  | 'SHIFT_MANAGE'
  | 'CAST_MANAGE'
  | 'CAST_INVITE'
  | 'CAST_FIELD_DEF_VIEW'
  | 'CAST_FIELD_DEF_MANAGE'
  | 'STORE_PROFILE_MANAGE'
  | 'STORE_MENU_VIEW';

// ログイン後の着地先（サーバ側が能力目録から導出する — /me の console）
export type PlatformConsole = 'platform' | 'store' | 'none';

// 平台ユーザーの店舗作用域種別
export type PlatformStoreScopeType = 'ALL_STORES' | 'SPECIFIC_STORES';

// 平台ログインリクエスト
export interface PlatformLoginRequest {
  email: string;
  password: string;
}

// 平台 /me レスポンス
export interface PlatformMeResponse {
  email: string;
  display_name: string;
  user_type: PlatformUserType;
  capabilities: PlatformCapability[];
  console: PlatformConsole;
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
}

// 平台自己プロフィール更新リクエスト
export interface PlatformMeUpdateRequest {
  display_name: string;
}

// 平台の授権店舗一覧の1件
export interface PlatformStore {
  id: number;
  name: string;
}

// 能力束への参照（id と名称）
export interface CapabilityBundleRef {
  id: number;
  name: string;
}

// 能力束一覧の1件（授与 UI の選択肢）
export interface CapabilityBundleResponse {
  id: number;
  name: string;
  capabilities: PlatformCapability[];
}

// スタッフ（能力束×店舗集合×精算範囲）の応答
export interface PlatformStaffResponse {
  id: number;
  email: string;
  display_name: string;
  enabled: boolean;
  bundles: CapabilityBundleRef[];
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
  settlement_scope_type: PlatformStoreScopeType | null;
  settlement_store_ids: number[];
  // 楽観ロック用バージョン（更新リクエストへそのまま往復する — #400）
  version: number;
}

// スタッフ新規作成リクエスト
export interface PlatformStaffCreateRequest {
  email: string;
  password: string;
  display_name: string;
  bundle_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
  settlement_scope_type?: PlatformStoreScopeType | null;
  settlement_store_ids?: number[];
}

// スタッフ授権編集リクエスト（enabled: 未指定=現状維持、false=停止、true=再開）
export interface PlatformStaffUpdateRequest {
  bundle_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
  settlement_scope_type?: PlatformStoreScopeType | null;
  settlement_store_ids?: number[];
  enabled?: boolean;
  // 楽観ロック用バージョン（応答の version をそのまま返送。不一致は 409 — #400）
  version: number;
}

// 付与履歴の操作種別
export type GrantAction = 'GRANT' | 'CHANGE' | 'STOP' | 'RESUME';

// 付与履歴の1件
export interface GrantHistoryEntryResponse {
  id: number;
  actor_email: string;
  action: GrantAction;
  detail: string;
  created_at: string;
}
