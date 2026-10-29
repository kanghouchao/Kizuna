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

// 本人種別。バックエンド user/domain/UserType.java には SERVICE（対話ログイン不可の
// サービスID）もあるが、ログイン応答・/me には現れないため意図的に載せない（fail-closed）。
export type UserType = 'STAFF' | 'CAST' | 'MEMBER';

// 権限コード（バックエンド user/domain/PermissionCode.java と対応）。
// ワイヤ上は接頭辞なしの素の enum 名で、PERM_ 形式は JWT の authorities 内部だけに存在する。
export type PermissionCode =
  | 'STORE_MANAGE'
  | 'ROLE_MANAGE'
  | 'STAFF_CREDENTIAL_MANAGE'
  | 'STAFF_MANAGE'
  | 'SYSTEM_CONFIG_MANAGE'
  | 'ASSET_MANAGE'
  | 'BENEFIT_MANAGE'
  | 'SERVICE_ID_MANAGE'
  | 'TASK_MANAGE'
  | 'TASK_EXECUTE'
  | 'POINT_EXPIRE'
  | 'SURVEY_VIEW'
  | 'SURVEY_MANAGE'
  | 'SURVEY_RECORD'
  | 'REVIEW_VIEW'
  | 'REVIEW_MANAGE'
  | 'REVIEW_MODERATE'
  | 'NOTIFICATION_VIEW'
  | 'NOTIFICATION_MANAGE'
  | 'NOTIFICATION_SEND'
  | 'NOTIFICATION_DELIVER'
  | 'AUDIT_VIEW'
  | 'ADVERTISING_COST_VIEW'
  | 'ADVERTISING_COST_MANAGE'
  | 'ADVERTISING_COST_EXPORT'
  | 'ADVERTISING_COST_SET_VIEW'
  | 'ADVERTISING_COST_SET_EXPORT'
  | 'OPERATIONAL_REPORT_VIEW'
  | 'OPERATIONAL_REPORT_EXPORT'
  | 'STORE_VIEW'
  | 'ORDER_SET_VIEW'
  | 'ORDER_MANAGE'
  | 'SERVICE_MANAGE'
  | 'ORDER_CORRECT'
  | 'CUSTOMER_MANAGE'
  | 'CUSTOMER_MERGE'
  | 'POINT_ADJUST'
  | 'SHIFT_MANAGE'
  | 'REMUNERATION_VIEW'
  | 'GUARANTEE_MANAGE'
  | 'BONUS_AWARD'
  | 'REMUNERATION_CORRECT'
  | 'CAST_PERSON_VIEW'
  | 'MEMBER_VIEW'
  | 'MEMBER_WITHDRAW'
  | 'CAST_MANAGE'
  | 'CAST_INVITE'
  | 'CAST_FIELD_DEF_VIEW'
  | 'CAST_FIELD_DEF_MANAGE'
  | 'STORE_PROFILE_MANAGE'
  | 'RECRUITMENT_VIEW'
  | 'RECRUITMENT_MANAGE'
  | 'RECRUITMENT_DECIDE'
  | 'RECRUITMENT_ATTACHMENT_VIEW'
  | 'RECRUITMENT_ATTACHMENT_MANAGE';

// セッションの種別（session-kind cookie の値）。従業員は本人種別 STAFF で単一の管理画面 admin に入り、
// CAST / MEMBER はそれぞれの portal に入る。値はサーバの /me の user_type から画面側が決める。
export type SessionKind = 'admin' | 'cast' | 'member';

// ユーザーの店舗作用域種別
export type StoreScopeType = 'ALL_STORES' | 'SPECIFIC_STORES';

// ログインリクエスト
export interface LoginRequest {
  email: string;
  password: string;
}

// /me レスポンス
export interface MeResponse {
  email?: string;
  display_name?: string;
  user_type?: UserType;
  permissions?: PermissionCode[];
  // LINE アカウントと連携済みか。Java 側が primitive の boolean のため、キーは必ず応答に含まれる。
  line_linked: boolean;
}

// 自己プロフィール更新リクエスト
export interface MeUpdateRequest {
  display_name: string;
}

// 店舗の稼働状態。準備中の店舗は店長が店舗設定から明示的に稼働中へ移す。
export type StoreStatus = 'PREPARING' | 'ACTIVE';

// 授権店舗一覧の1件
export interface AuthorizedStore {
  id?: number;
  name?: string;
  short_name?: string;
  status?: StoreStatus;
}

// 権限目録の1件（ロール編集 UI の選択肢）。label は画面に出す日本語名。
export interface PermissionResponse {
  code?: PermissionCode;
  label?: string;
}

export type PermissionGroupCode = 'BUSINESS' | 'CAST' | 'CUSTOMER' | 'ORGANIZATION' | 'OPERATION';

// 権限目録の業務領域ごとの束。組の順序と見出しはサーバが決める。
export interface PermissionGroupResponse {
  group?: PermissionGroupCode;
  label?: string;
  permissions?: PermissionResponse[];
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
  // 既定ロール。改名・削除は拒否される（権限は編集できる）。
  // system と permission_count は Java 側が primitive のため、キーは必ず応答に含まれる。
  system: boolean;
  permission_count: number;
}

// ロール詳細（GET /roles/{id} と作成・更新の応答）
export interface RoleResponse {
  id?: number;
  name?: string;
  // 既定ロール。改名・削除は拒否される（権限は編集できる）。
  // system と version は Java 側が primitive のため、キーは必ず応答に含まれる。
  system: boolean;
  permissions?: PermissionCode[];
  // 楽観ロック用バージョン（更新リクエストへそのまま往復する）
  version: number;
}

// ロール新規作成リクエスト
export interface RoleCreateRequest {
  name: string;
  permissions: PermissionCode[];
}

// ロール更新リクエスト
export interface RoleUpdateRequest {
  name: string;
  permissions: PermissionCode[];
  // 楽観ロック用バージョン（応答の version をそのまま返送。不一致は 409）
  version: number;
}

// 従業員（ロール×店舗集合）の応答。
// リクエストは role_ids（id の配列）なのに応答は roles（id と名称の対）という非対称に注意。
// editable は委譲守衛（対象の権限並集と店舗集合が行使者の部分集合か）のサーバ側判定で、行使者ごとに変わる。
// 一覧に出るか（行使者の店舗集合と交わるか）とは別の軸で、false の行は見えるが編集・停止・再設定できない。
export interface StaffResponse {
  id?: number;
  email?: string;
  display_name?: string;
  // enabled / version / editable は Java 側が primitive のため、キーは必ず応答に含まれる。
  enabled: boolean;
  roles?: RoleRef[];
  store_scope_type?: StoreScopeType;
  store_ids?: number[];
  // 楽観ロック用バージョン（更新リクエストへそのまま往復する）
  version: number;
  editable: boolean;
}

// 従業員新規作成リクエスト
export interface StaffCreateRequest {
  email: string;
  password: string;
  display_name: string;
  role_ids: number[];
  store_scope_type: StoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
}

// 従業員の授権編集リクエスト。停止・再開は専用端点（suspension / resumption）が担うのでここには無い
// （サーバは未知のキーを 400 で弾くため、enabled を混ぜると更新そのものが通らない）。
export interface StaffUpdateRequest {
  role_ids: number[];
  store_scope_type: StoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
  // 楽観ロック用バージョン（応答の version をそのまま返送。不一致は 409）
  version: number;
}

// サービスID一覧 1 件の要約。楽観ロック用 version は詳細（ServiceIdentityResponse）だけが持ち、
// 授権編集は詳細を取り直してから始める（範式は RoleSummaryResponse / RoleResponse）。
export interface ServiceIdentitySummaryResponse {
  id?: number;
  display_name?: string;
  // enabled は Java 側が primitive のため、キーは必ず応答に含まれる。
  enabled: boolean;
  roles?: RoleRef[];
  store_scope_type?: StoreScopeType;
  store_ids?: number[];
}

// サービスID（本人種別 SERVICE・資格情報なし）の詳細応答（GET /{id} と作成・更新の応答）。
// email を持たないこと以外は StaffResponse と同形で、
// リクエストの role_ids と応答の roles の非対称も同じ。
export interface ServiceIdentityResponse {
  id?: number;
  display_name?: string;
  // enabled と version は Java 側が primitive のため、キーは必ず応答に含まれる。
  enabled: boolean;
  roles?: RoleRef[];
  store_scope_type?: StoreScopeType;
  store_ids?: number[];
  // 楽観ロック用バージョン（更新リクエストへそのまま往復する）
  version: number;
}

// サービスID新規作成リクエスト。資格情報（email/password）はサーバ側に欄が無い。
export interface ServiceIdentityCreateRequest {
  display_name: string;
  role_ids: number[];
  store_scope_type: StoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
}

// サービスID授権編集リクエスト。停止・再開は専用端点が担うのでここには無い
// （サーバは未知のキーを 400 で弾くため、enabled を混ぜると更新そのものが通らない）。
export interface ServiceIdentityUpdateRequest {
  role_ids: number[];
  store_scope_type: StoreScopeType;
  // Java 側に必須注解が無い（ALL_STORES のときは送らなくてよい）
  store_ids?: number[];
  // 楽観ロック用バージョン（応答の version をそのまま返送。不一致は 409）
  version: number;
}

// パスワード再設定の応答。仮パスワードの生値はこの応答にしか現れず、以後どこからも取り出せない。
export interface StaffAccountPasswordResetResponse {
  temporary_password: string;
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

// 初期設定（最初の管理者の登録）リクエスト。ロールと店舗集合はサーバーが決めるため送らない。
export interface InitialSetupRequest {
  email: string;
  password: string;
  display_name: string;
}
