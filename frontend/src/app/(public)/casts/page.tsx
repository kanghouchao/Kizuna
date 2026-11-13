import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('キャスト一覧');
}

export default async function CastsRoute({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  return <StoreSitePage page="casts" searchParams={await searchParams} />;
}
