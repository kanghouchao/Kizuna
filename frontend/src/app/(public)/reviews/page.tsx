import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('口コミ');
}

/** URL の page は 1 起点。数値でない・1 未満は先頭ページとして扱う。 */
export default async function ReviewsRoute({
  searchParams,
}: {
  searchParams: Promise<{ page?: string | string[] }>;
}) {
  const { page } = await searchParams;
  const requested = Number(Array.isArray(page) ? page[0] : page);
  const index = Number.isInteger(requested) && requested >= 1 ? requested - 1 : 0;
  return <StoreSitePage page="reviews" pageProps={{ page: index }} />;
}
