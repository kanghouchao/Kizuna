// システム設定レスポンス
export interface SystemConfigResponse {
  id: number;
  config_key: string;
  // 秘匿設定はバックエンドでマスクされ JSON に含まれないため optional
  config_value?: string;
  value_type: 'STRING' | 'NUMBER' | 'BOOLEAN';
  secret: boolean;
  category: string;
  description?: string;
  created_at: string;
  updated_at: string;
}

// システム設定更新リクエスト
export interface SystemConfigUpdateRequest {
  config_key: string;
  config_value: string;
}
