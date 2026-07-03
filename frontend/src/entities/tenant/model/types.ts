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
