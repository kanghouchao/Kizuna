// 管理者（Central 側）
export interface Admin {
  id: number;
  username: string;
  role: 'super_admin' | 'admin';
}

// 店舗ユーザー情報（Store 側）
export interface StoreUserResponse {
  id: string;
  email: string;
  nickname: string;
  role: string;
}

// パスワード変更リクエスト
export interface PasswordChangeRequest {
  current_password: string;
  new_password: string;
}

// 店舗ユーザーのプロフィール更新リクエスト
export interface StoreUserProfileUpdateRequest {
  nickname: string;
}

// ログインリクエスト
export interface LoginRequest {
  username: string;
  password: string;
}

// 認証レスポンス
export interface LoginResponse {
  token: string;
  expires_at: number;
}

// 登録リクエスト
export interface RegisterRequest {
  token: string;
  email: string;
  password: string;
}

export interface RegisterResponse {
  tenant_domain: string;
  login_url: string;
  tenant_name?: string;
}

// 平台ロール（バックエンド user/domain/PlatformRole.java と対応）
export type PlatformRole = 'HQ_ADMIN' | 'STORE_MANAGER' | 'STORE_STAFF' | 'CAST' | 'MEMBER';

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
  role: PlatformRole;
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

// スタッフ（ロール×店舗集合）の応答
export interface PlatformStaffResponse {
  id: number;
  email: string;
  display_name: string;
  role: PlatformRole;
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
}

// スタッフ新規作成リクエスト
export interface PlatformStaffCreateRequest {
  email: string;
  password: string;
  display_name: string;
  role: PlatformRole;
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
}

// スタッフ権限編集リクエスト
export interface PlatformStaffUpdateRequest {
  role: PlatformRole;
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
}
