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

  const tenantName = cookieStore.get('x-mw-store-name')?.value || 'Store';
  const title = pageTitle ? `${pageTitle}｜${tenantName}` : tenantName;
  const description = pageTitle
    ? `${tenantName}の${pageTitle}ページです。`
    : `${tenantName}の公式サイトです。キャスト情報やキャンペーン情報をご覧いただけます。`;

  return {
    title,
    description,
    openGraph: { title, description, type: 'website' },
  };
}
