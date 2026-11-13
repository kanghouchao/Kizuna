// PROTOTYPE（#1356）: 3 テンプレートの版面を既存ルート上で ?variant= で見比べるための使い捨てコード。
// 本番ビルドでは StoreSitePage が一切読まない。main へは入れない。
import type { PublicStore } from '@/shared/api/index.server';
import type { Cast, SiteConfig } from '../model/types';

export const PROTO_VARIANTS = ['default', 'modern', 'classic'] as const;
export type ProtoVariant = (typeof PROTO_VARIANTS)[number];

export const PROTO_VARIANT_NAMES: Record<ProtoVariant, string> = {
  default: '高級・静',
  modern: '明るい・ポップ',
  classic: '和風・落ち着き',
};

export const PROTO_PAGES = ['page', 'casts', 'cast-detail'] as const;
export type ProtoPage = (typeof PROTO_PAGES)[number];

/** real = 取得したまま / bare = 画像・文言・キャストが全て未設定 / many = キャスト 40 人 */
export const PROTO_DATA_MODES = ['real', 'bare', 'many'] as const;
export type ProtoDataMode = (typeof PROTO_DATA_MODES)[number];

/** 各 variant のページが受け取る props。データは殻が取得済みで、variant は描くだけ。 */
export interface ProtoPageProps {
  store: PublicStore;
  siteConfig: SiteConfig;
  casts: Cast[];
  /** cast-detail のときだけ入る */
  cast?: Cast;
  /** 現在の ?variant=…&data=… 。内部リンクへ protoHref で付け回す */
  q: string;
}

/** 全テンプレートが必ず持つページ（内容はテンプレートに依らない）。並べ方・形は variant の自由。 */
export const SITE_NAV = [
  { href: '/', label: 'TOP' },
  { href: '/casts', label: 'キャスト' },
  { href: '/schedule', label: '出勤表' },
  { href: '/news', label: 'お知らせ' },
  { href: '/reviews', label: '口コミ' },
  { href: '/menu', label: '料金' },
  { href: '/about', label: '店舗情報' },
  { href: '/reservation', label: 'ご予約' },
] as const;

/** prototype が持つ 3 ページ（/ ・/casts ・/casts/:id）へのリンクにだけ variant を引き継ぐ。 */
export function protoHref(path: string, q: string): string {
  const carries = path === '/' || path === '/casts' || path.startsWith('/casts/');
  return carries ? `${path}${q}` : path;
}

/** キャスト詳細のプロフィール行。値の無い項目は行ごと出さない（現行と同じ規則）。 */
export function castProfileRows(cast: Cast): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  if (cast.age) rows.push({ label: '年齢', value: `${cast.age}歳` });
  if (cast.height) rows.push({ label: '身長', value: `${cast.height}cm` });
  if (cast.bust) rows.push({ label: 'バスト', value: `${cast.bust}` });
  if (cast.waist) rows.push({ label: 'ウエスト', value: `${cast.waist}` });
  if (cast.hip) rows.push({ label: 'ヒップ', value: `${cast.hip}` });
  cast.custom_fields?.forEach(f => rows.push({ label: f.label ?? '', value: f.value ?? '' }));
  return rows;
}
