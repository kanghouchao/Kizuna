import { CastCustomFieldView } from '@/entities/cast';
import { PartnerLink, SnsLink } from '@/entities/store-profile';

/**
 * キャスト情報
 */
export interface Cast {
  id: string;
  name: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  custom_fields?: CastCustomFieldView[];
}

/**
 * サイト設定情報
 */
export interface SiteConfig {
  logo_url?: string;
  banner_url?: string;
  description?: string;
  catch_copy?: string;
  address?: string;
  phone?: string;
  business_hours?: string;
  pricing_description?: string;
  custom_texts?: Record<string, string>;
  mv_url?: string;
  mv_type: 'image' | 'video';
  sns_links?: SnsLink[];
  partner_links?: PartnerLink[];
}

/**
 * ストアフロント用データの集約型
 */
export interface StorefrontData {
  casts: Cast[];
  siteConfig: SiteConfig;
}

/**
 * 公開出勤表のシフト情報
 */
export interface PublicShift {
  cast_id: string;
  cast_name: string;
  cast_photo_url?: string;
  start_time: string;
  end_time: string;
}
