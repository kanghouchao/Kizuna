import type { Metadata } from 'next';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata('出勤表');
}

export default function ScheduleRoute() {
  return <StoreSitePage page="schedule" />;
}
