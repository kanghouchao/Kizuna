import { CastCustomFieldView } from '@/entities/cast';
import { PartnerLink, SnsLink } from '@/entities/store-profile';
import type { Page } from '@/shared/api';
import type { PublicStore } from '@/shared/api/index.server';

/**
 * キャスト情報
 */
export interface Cast {
  id?: string;
  name?: string;
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
  template_key?: string;
  logo_url?: string;
  banner_url?: string;
  description?: string;
  catch_copy?: string;
  address?: string;
  phone?: string;
  business_hours?: string;
  access_note?: string;
  pricing_description?: string;
  mv_url?: string;
  mv_type?: 'image' | 'video';
  sns_links?: SnsLink[];
  partner_links?: PartnerLink[];
}

/**
 * 公開出勤表のシフト情報
 */
export interface PublicShift {
  cast_id?: string;
  cast_name?: string;
  cast_photo_url?: string;
  start_time?: string;
  end_time?: string;
}

/**
 * 公式サイトに掲出中の口コミ。received_on は店舗の現地日付（YYYY-MM-DD）
 */
export interface PublicReview {
  display_name?: string | null;
  body: string;
  received_on: string;
}

/**
 * 公式サイトに掲出中の記事の一覧行。publish_from は公開開始の日時（未設定なら null）
 */
export interface PublicArticleSummary {
  id: string;
  title: string;
  cover_image_url?: string;
  publish_from: string | null;
}

/** 公式サイトに掲出中の記事。body はプレーンテキスト */
export interface PublicArticle extends PublicArticleSummary {
  body: string;
}

/** 公開サイトの固定ページ名（templates/<key>/<page>.tsx 契約）。 */
export type StoreSitePageName =
  | 'page'
  | 'casts'
  | 'cast-detail'
  | 'schedule'
  | 'menu'
  | 'about'
  | 'reservation'
  | 'reviews'
  | 'news'
  | 'news-detail';

/** 公開ルートが殻へ渡す、開くページとその引数。pageIndex は 0 起点。 */
export type StoreSiteRoute =
  | { page: 'page' | 'casts' | 'schedule' | 'menu' | 'about' | 'reservation' }
  | { page: 'cast-detail'; castId: string }
  | { page: 'news-detail'; articleId: string }
  | { page: 'reviews' | 'news'; pageIndex: number };

/** ページごとに殻が取得するデータ。一覧の null は取得の失敗で、0 件と区別する。 */
export interface StoreSitePageData {
  page: { casts: Cast[] };
  casts: { casts: Cast[] };
  'cast-detail': { cast: Cast };
  schedule: { shifts: PublicShift[] };
  menu: object;
  about: object;
  reservation: object;
  reviews: { reviews: Page<PublicReview> | null; page: number };
  news: { articles: Page<PublicArticleSummary> | null; page: number };
  'news-detail': { article: PublicArticle };
}

/**
 * テンプレートのページが受け取る props。店舗はサーバがリクエストの Host から解決した値で、
 * データはすべて殻が取得済み。テンプレートのページは自分では取りに行かない。
 */
export type StoreSitePageProps<P extends StoreSitePageName> = StoreSitePageData[P] & {
  store: PublicStore;
  siteConfig: SiteConfig;
};
