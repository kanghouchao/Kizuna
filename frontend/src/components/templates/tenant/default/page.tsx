import { cookies } from 'next/headers';

export default async function Page() {
  const cookieStore = await cookies();
  const tenantId = cookieStore.get('x-mw-tenant-id')?.value;
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || '';

  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-24">
      <h1 className="text-3xl font-bold mb-4 text-indigo-700">Default テンプレート</h1>
      <p className="text-gray-600">これはデフォルトのテンプレートページです。</p>
      <div className="mt-8 text-gray-800">
        <div>租户ID: {tenantId}</div>
        <div>租户名称: {tenantName}</div>
      </div>
    </main>
  );
}
