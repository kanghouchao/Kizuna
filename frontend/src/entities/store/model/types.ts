// 店舗
export interface Store {
  id?: string;
  name?: string;
  email?: string;
  domain?: string;
  created_at?: string;
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
  // Java 側が primitive の long のため、キーは必ず応答に含まれる
  total: number;
}
