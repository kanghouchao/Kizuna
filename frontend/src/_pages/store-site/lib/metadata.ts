import type { Metadata } from 'next';
import { cookies } from 'next/headers';

/**
 * 公開站各ページの SEO metadata を店名から生成する（issue #223 Phase 2）。
 * store ドメイン以外では素の title を返す（ページ本体は notFound になる）。
 */
export async function storeSiteMetadata(pageTitle?: string): Promise<Metadata> {
  const cookieStore = await cookies();
  if (cookieStore.get('x-mw-role')?.value !== 'store') {
    return { title: 'Kizuna Platform' };
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
