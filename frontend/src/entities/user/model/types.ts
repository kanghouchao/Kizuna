// パスワード変更リクエスト
export interface PasswordChangeRequest {
  current_password: string;
  new_password: string;
}

// 認証レスポンス
export interface LoginResponse {
  token?: string;
  // Java 側が primitive の long のため、キーは必ず応答に含まれる
  expires_at: number;
}

// 本人種別（バックエンド user/domain/UserType.java と対応）
export type PlatformUserType = 'STAFF' | 'CAST' | 'MEMBER';

// 権限コード（バックエンド user/domain/PermissionCode.java と対応）。
// ワイヤ上は接頭辞なしの素の enum 名で、PERM_ 形式は JWT の authorities 内部だけに存在する。
export type PlatformPermission =
  | 'STORE_MANAGE'
  | 'STAFF_MANAGE'
  | 'SYSTEM_CONFIG_MANAGE'
  | 'PLATFORM_MENU_VIEW'
  | 'PLATFORM_ASSET_MANAGE'
  | 'STORE_VIEW'
  | 'ORDER_SET_MANAGE'
  | 'ORDER_MANAGE'
  | 'ORDER_CORRECT'
  | 'CUSTOMER_MANAGE'
  | 'CUSTOMER_MERGE'
  | 'POINT_ADJUST'
  | 'SHIFT_MANAGE'
  | 'CAST_MANAGE'
  | 'CAST_INVITE'
  | 'CAST_FIELD_DEF_VIEW'
  | 'CAST_FIELD_DEF_MANAGE'
  | 'STORE_PROFILE_MANAGE'
  | 'STORE_MENU_VIEW';

// ログイン後の着地先（サーバ側が権限目録から導出する — /me の console）。
// 小文字 3 値。権限目録（/platform/permissions）の console は大文字 3 値の別型（PermissionConsole）。
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
  email?: string;
  display_name?: string;
  user_type?: PlatformUserType;
  permissions?: PlatformPermission[];
  console?: PlatformConsole;
  // 店舗文脈（X-Store-ID）を確立できるか。JWT storeBridge claim と同源でサーバ側が権限目録から導出する。
  // Java 側が primitive の boolean のため、キーは必ず応答に含まれる。
  store_bridge: boolean;
  store_scope_type?: PlatformStoreScopeType;
  store_ids?: number[];
  // LINE アカウントと連携済みか。Java 側が primitive の boolean のため、キーは必ず応答に含まれる。
  line_linked: boolean;
}

// 平台自己プロフィール更新リクエスト
export interface PlatformMeUpdateRequest {
  display_name: string;
}

// 平台の授権店舗一覧の1件
export interface PlatformStore {
  id?: number;
  name?: string;
}

// 権限目録（/platform/permissions）の所属コンソール。大文字 3 値で、
// /me の console（小文字 3 値の PlatformConsole）とはキー名が同じでも値域が別物。
export type PermissionConsole = 'PLATFORM' | 'STORE' | 'SHARED';

// 権限目録の1件（ロール編集 UI の選択肢）
export interface PermissionResponse {
  code?: PlatformPermission;
  console?: PermissionConsole;
}

// ロールへの参照（スタッフ応答の埋め込み）。
// name はサーバの non_null 方針でキーごと欠落しうるため任意扱いにする。
export interface RoleRef {
  id?: number;
  name?: string;
}

// ロール一覧の1件（要約）。権限は個数のみで、コードの列挙は詳細（RoleResponse）が持つ。
export interface RoleSummaryResponse {
  id?: number;
  name?: string;
  // 平台既定ロール。改名・権限変更・削除がいずれも拒否される。
  // system と permission_count は Java 側が primitive のため、キーは必ず応答に含まれる。
  system: boolean;
  permission_count: number;
}

// ロール詳細（GET /platform/roles/{id} と作成・更新の応答）
export interface RoleResponse {
  id?: number;
  name?: string;
  // 平台既定ロール。改名・権限変更・削除がいずれも拒否される。
  // system と version は Java 側が primitive のため、キーは必ず応答に含まれる。
  system: boolean;
  permissions?: PlatformPermission[];
  // 楽観ロック用バージョン（更新リクエストへそのまま往復する）
  version: number;
}

// ロール新規作成リクエスト
export interface RoleCreateRequest {
  name: string;
  permissions: PlatformPermission[];
}

// ロール更新リクエスト
export interface RoleUpdateRequest {
  name: string;
  permissions: PlatformPermission[];
  // 楽観ロック用バージョン（応答の version をそのまま返送。不一致は 409）
  version: number;
}

// スタッフ（ロール×店舗集合）の応答。
// リクエストは role_ids（id の配列）なのに応答は roles（id と名称の対）という非対称に注意。
export interface PlatformStaffResponse {
  id?: number;
  email?: string;
  display_name?: string;
  // enabled と version は Java 側が primitive のため、キーは必ず応答に含まれる。
  enabled: boolean;
  roles?: RoleRef[];
  store_scope_type?: PlatformStoreScopeType;
  store_ids?: number[];
  // 楽観ロック用バージョン（更新リクエストへそのまま往復する）
  version: number;
}

// スタッフ新規作成リクエスト
export interface PlatformStaffCreateRequest {
  email: string;
  password: string;
  display_name: string;
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
}

// スタッフ授権編集リクエスト（enabled: 未指定=現状維持、false=停止、true=再開）
export interface PlatformStaffUpdateRequest {
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
  enabled?: boolean;
  // 楽観ロック用バージョン（応答の version をそのまま返送。不一致は 409）
  version: number;
}

// LINE ログインの公開設定。enabled=false のとき入口自体を描画しない。
export interface LineConfigResponse {
  // Java 側が primitive の boolean のため、キーは必ず応答に含まれる。
  enabled: boolean;
  channel_id?: string;
}

// LINE 認可コードの引き換え要求（ログインと連携で同形）。
// redirect_uri は認可要求時と同一値でなければならない。
export interface LineAuthorizationRequest {
  code: string;
  redirect_uri: string;
  code_verifier: string;
}

// LINE ログインの応答。未登録の LINE ユーザーには token ではなく登録チケットが返る。
export type LineLoginResponse =
  | ({ registered: true } & LoginResponse)
  | { registered: false; registration_ticket?: string; display_name?: string };

// LINE 経由の会員登録要求（登録チケットと入力 2 項目。同意チェックは画面側の関門で送信しない）
export interface LineRegisterRequest {
  registration_ticket: string;
  display_name: string;
  email: string;
}
