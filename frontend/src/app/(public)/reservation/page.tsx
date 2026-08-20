import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('ご予約');
}

export default function ReservationRoute() {
  return <StoreSitePage page="reservation" />;
}
