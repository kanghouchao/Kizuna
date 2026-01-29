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
  tenantDomain: string;
  loginUrl: string;
  tenantName?: string;
}
