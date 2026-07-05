import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('キャスト紹介');
}

export default async function CastDetailRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreSitePage page="cast-detail" pageProps={{ castId: id }} />;
}
