import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('キャスト一覧');
}

export default function CastsRoute() {
  return <StoreSitePage page="casts" />;
}
