import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('料金');
}

export default function MenuRoute() {
  return <StoreSitePage page="menu" />;
}
