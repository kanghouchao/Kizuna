// 店舗
export interface Store {
  id: string;
  name: string;
  email: string;
  domain: string;
  domains: string[];
  is_active: boolean;
  created_at: string;
  updated_at?: string;
}

// 店舗作成リクエスト
export interface CreateStoreRequest {
  name: string;
  domain: string;
  email: string;
}

// 店舗更新リクエスト
export interface UpdateStoreRequest {
  name: string;
  email: string;
}

// 店舗統計データ
export interface StoreStats {
  total: number;
  active: number;
  inactive: number;
  pending: number;
}
