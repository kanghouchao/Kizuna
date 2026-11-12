import { StoreDashboardPrototype } from '@/_pages/dashboard-prototype';

// 試作（捨てる前提）。案は ?variant=A|B|C で切り替える。
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ variant?: string | string[] }>;
}) {
  const { variant } = await searchParams;
  return <StoreDashboardPrototype variant={typeof variant === 'string' ? variant : 'A'} />;
}
