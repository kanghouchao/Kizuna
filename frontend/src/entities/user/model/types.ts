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
