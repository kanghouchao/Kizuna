import type { Metadata } from 'next';
import { StoreSitePage, pageIndexOf, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('お知らせ');
}

export default async function NewsRoute({
  searchParams,
}: {
  searchParams: Promise<{ page?: string | string[] }>;
}) {
  const { page } = await searchParams;
  return <StoreSitePage page="news" pageIndex={pageIndexOf(page)} />;
}
