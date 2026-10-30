import type { Metadata } from 'next';
import { resolveHost } from '@/shared/api/index.server';

/**
 * 公開站各ページの SEO metadata を店名から生成する。
 * store ドメイン以外では title を持たず、根 layout の既定 title を継承する（ページ本体は notFound になる）。
 */
export async function storeSiteMetadata(pageTitle?: string): Promise<Metadata> {
  const host = await resolveHost();
  if (host.kind !== 'store') {
    return {};
  }

  const storeName = host.store.name;
  const title = pageTitle ? `${pageTitle}｜${storeName}` : storeName;
  const description = pageTitle
    ? `${storeName}の${pageTitle}ページです。`
    : `${storeName}の公式サイトです。キャスト情報やキャンペーン情報をご覧いただけます。`;

  return {
    title,
    description,
    openGraph: { title, description, type: 'website' },
  };
}
