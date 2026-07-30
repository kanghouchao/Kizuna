// SNSリンク。更新リクエストの入れ子でもあり、Java 側は platform・url とも @NotBlank。
// 同じ型を請求と応答の双方で使うため、厳しい側（請求）の必須性を採る。
export interface SnsLink {
  platform: string;
  url: string;
  label?: string;
}

// パートナーリンク。更新リクエストの入れ子でもあり、Java 側は name・url とも @NotBlank。
export interface PartnerLink {
  name: string;
  url: string;
  logo_url?: string;
}

// 店舗サイト設定レスポンス
export interface StoreProfileResponse {
  id?: string;
  template_key?: string;
  logo_url?: string;
  banner_url?: string;
  mv_url?: string;
  mv_type?: string;
  description?: string;
  catch_copy?: string;
  address?: string;
  phone?: string;
  business_hours?: string;
  pricing_description?: string;
  custom_texts?: Record<string, string>;
  sns_links?: SnsLink[];
  partner_links?: PartnerLink[];
  created_at?: string;
  updated_at?: string;
}

// 店舗サイト設定更新リクエスト
export interface StoreProfileUpdateRequest {
  template_key?: string;
  logo_url?: string;
  banner_url?: string;
  mv_url?: string;
  mv_type?: string;
  description?: string;
  catch_copy?: string;
  address?: string;
  phone?: string;
  business_hours?: string;
  pricing_description?: string;
  custom_texts?: Record<string, string>;
  sns_links?: SnsLink[];
  partner_links?: PartnerLink[];
}
