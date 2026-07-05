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

// 店舗サイト設定レスポンス
export interface StoreProfileResponse {
  id: number;
  template_key: string;
  logo_url?: string;
  banner_url?: string;
  mv_url?: string;
  mv_type: string;
  description?: string;
  catch_copy?: string;
  address?: string;
  phone?: string;
  business_hours?: string;
  pricing_description?: string;
  custom_texts?: Record<string, string>;
  sns_links: SnsLink[];
  partner_links: PartnerLink[];
  created_at: string;
  updated_at: string;
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
