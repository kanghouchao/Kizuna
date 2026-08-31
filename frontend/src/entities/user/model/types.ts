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
  | 'ROLE_MANAGE'
  | 'STAFF_ACCOUNT_MANAGE'
  | 'STORE_STAFF_MANAGE'
  | 'SYSTEM_CONFIG_MANAGE'
  | 'PLATFORM_MENU_VIEW'
  | 'PLATFORM_ASSET_MANAGE'
  | 'BENEFIT_MANAGE'
  | 'EMERGENCY_ELEVATE'
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

// スタッフ授権編集リクエスト。停止・再開はアカウント管理の専用端点が担うのでここには無い
// （サーバは未知のキーを 400 で弾くため、enabled を混ぜると更新そのものが通らない）。
export interface PlatformStaffUpdateRequest {
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
  // 楽観ロック用バージョン（応答の version をそのまま返送。不一致は 409）
  version: number;
}

// パスワード再設定の応答。仮パスワードの生値はこの応答にしか現れず、以後どこからも取り出せない。
export interface StaffAccountPasswordResetResponse {
  temporary_password: string;
}

// アカウント面の一覧 1 件。停止・再開はどちらも冪等で版を往復しないため version を持たず、
// ロールは表示専用（この面からは授権を動かせない）。
export interface StaffAccountSummaryResponse {
  id?: number;
  email?: string;
  display_name?: string;
  // enabled は Java 側が primitive のため、キーは必ず応答に含まれる。
  enabled: boolean;
  roles?: RoleRef[];
}

// 店舗スタッフ（店舗側ロールのみを持つアカウント）の応答。
// editable は防提権守衛 G3 のサーバ側判定で、行使者ごとに変わる。
// 表示可否（一覧に出るか）とは別の軸で、false の行は見えるが編集・停止できない。
export interface StoreStaffResponse {
  id?: number;
  email?: string;
  display_name?: string;
  // enabled / version / editable は Java 側が primitive のため、キーは必ず応答に含まれる。
  enabled: boolean;
  roles?: RoleRef[];
  store_scope_type?: PlatformStoreScopeType;
  store_ids?: number[];
  version: number;
  editable: boolean;
}

// 店舗スタッフ新規作成リクエスト
export interface StoreStaffCreateRequest {
  email: string;
  password: string;
  display_name: string;
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
}

// 店舗スタッフ授権編集リクエスト（enabled: 未指定=現状維持、false=停止、true=再開）
export interface StoreStaffUpdateRequest {
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  store_ids?: number[];
  enabled?: boolean;
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

// 店長設定（店舗管理ページの節）の一覧 1 件。
// この面が扱うのは「この店舗の店長か否か」だけなので、ロール・担当店舗集合は返らない。
export interface StoreManagerResponse {
  id?: number;
  email?: string;
  display_name?: string;
  // Java 側が primitive の boolean のため、キーは必ず応答に含まれる。
  enabled: boolean;
}

// 任命できる既存アカウントの候補 1 件。母集団が有効なアカウントに限られるため状態は持たない。
export interface StoreManagerCandidateResponse {
  id?: number;
  email?: string;
  display_name?: string;
}

// 店長任命リクエスト。user_id を送れば既存アカウントの任命、
// 残り 3 項目を送れば新規作成しての任命で、混在・欠落はいずれも 400。
export type StoreManagerAppointRequest =
  { user_id: number } | { email: string; password: string; display_name: string };

// 緊急昇格の発動リクエスト。パスワード再入力は発動の直前関門（セッション奪取だけでは越えられない）
export interface EmergencyElevationActivationRequest {
  store_id: number;
  reason: string;
  password: string;
}

// 緊急昇格の発動レスポンス。昇格トークンの生値はこの応答にしか現れない（履歴からは取り直せない）
export interface EmergencyElevationActivationResponse {
  id?: number;
  token?: string;
  // Java 側が primitive の long のため、キーは必ず応答に含まれる
  expires_at: number;
}

// 履歴一覧の実効状態。記録の状態列は期限切れを持たず、期限の比較はサーバの読み口が行う
export type EmergencyElevationStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';

// 緊急昇格の履歴一覧 1 件。撤回欄は未撤回の行でキーごと欠落する（non_null 方針）
export interface EmergencyElevationSummary {
  id?: number;
  activated_by_name?: string;
  target_store_id?: number;
  store_name?: string;
  reason?: string;
  activated_at?: string;
  expires_at?: string;
  status?: EmergencyElevationStatus;
  revoked_by_name?: string;
  revoked_at?: string;
}
