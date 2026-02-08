// ページネーションレスポンス
export interface PaginatedResponse<T> {
  data: T[];
  current_page: number;
  from: number;
  last_page: number;
  per_page: number;
  to: number;
  total: number;
  first_page_url: string;
  last_page_url: string;
  next_page_url: string | null;
  prev_page_url: string | null;
}

// 管理者
export interface Admin {
  id: number;
  username: string;
  role: 'super_admin' | 'admin';
}

export interface MenuVO {
  name: string;
  path?: string;
  icon?: string;
  items?: MenuVO[];
}

// 認証レスポンス
export interface LoginResponse {
  token: string;
  expires_at: number;
}

// テナント
export interface Tenant {
  id: string;
  name: string;
  email: string;
  domain: string;
  domains: string[];
  is_active: boolean;
  created_at: string;
  updated_at?: string;
}

// テナント作成リクエスト
export interface CreateTenantRequest {
  name: string;
  domain: string;
  email: string;
}

// テナント更新リクエスト
export interface UpdateTenantRequest {
  name: string;
  email: string;
}

// ダッシュボード統計データ
export interface DashboardStats {
  total_tenants: number;
  total_domains: number;
  monthly_tenants: number;
  active_domains: number;
}

// テナント統計データ
export interface TenantStats {
  total: number;
  active: number;
  inactive: number;
  pending: number;
}

// ログインリクエスト
export interface LoginRequest {
  username: string;
  password: string;
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

// SNSリンク
export interface SnsLink {
  platform: string;
  url: string;
  label?: string;
}

// パートナーリンク
export interface PartnerLink {
  name: string;
  url: string;
  logo_url?: string;
}

// テナント設定レスポンス
export interface TenantConfigResponse {
  id: number;
  template_key: string;
  logo_url?: string;
  banner_url?: string;
  mv_url?: string;
  mv_type: string;
  description?: string;
  sns_links: SnsLink[];
  partner_links: PartnerLink[];
  created_at: string;
  updated_at: string;
}

// テナント設定更新リクエスト
export interface TenantConfigUpdateRequest {
  template_key?: string;
  logo_url?: string;
  banner_url?: string;
  mv_url?: string;
  mv_type?: string;
  description?: string;
  sns_links?: SnsLink[];
  partner_links?: PartnerLink[];
}

// ファイルアップロードレスポンス
export interface FileUploadResponse {
  url: string;
  original_name: string;
  size: number;
}

// キャスト（Cast）レスポンス
export interface CastResponse {
  id: string;
  name: string;
  status: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
  created_at: string;
  updated_at: string;
}

// キャスト作成リクエスト
export interface CastCreateRequest {
  name: string;
  status?: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
}

// キャスト更新リクエスト
export interface CastUpdateRequest {
  name?: string;
  status?: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
}
