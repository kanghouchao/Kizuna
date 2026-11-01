import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('お知らせ');
}

export default async function NewsDetailRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreSitePage page="news-detail" pageProps={{ articleId: id }} />;
}
