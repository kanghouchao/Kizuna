import type { Metadata } from 'next';
import { StoreSitePage, pageIndexOf, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('口コミ');
}

export default async function ReviewsRoute({
  searchParams,
}: {
  searchParams: Promise<{ page?: string | string[] }>;
}) {
  const { page } = await searchParams;
  return <StoreSitePage page="reviews" pageIndex={pageIndexOf(page)} />;
}
