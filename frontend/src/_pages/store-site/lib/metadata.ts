import type { Metadata } from 'next';
import { cookies } from 'next/headers';

/**
 * 公開站各ページの SEO metadata を店名から生成する。
 * store ドメイン以外では title を持たず、根 layout の既定 title を継承する（ページ本体は notFound になる）。
 */
export async function storeSiteMetadata(pageTitle?: string): Promise<Metadata> {
  const cookieStore = await cookies();
  if (cookieStore.get('x-mw-role')?.value !== 'store') {
    return {};
  }

  const storeName = cookieStore.get('x-mw-store-name')?.value || 'Store';
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
