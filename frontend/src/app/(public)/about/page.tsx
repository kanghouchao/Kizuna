import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('店舗情報・アクセス');
}

export default function AboutRoute() {
  return <StoreSitePage page="about" />;
}
