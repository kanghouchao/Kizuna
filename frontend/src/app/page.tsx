import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';

/**
 * ページメタデータの生成
 */
export async function generateMetadata(): Promise<Metadata> {
  const cookieStore = await cookies();
  const role = cookieStore.get('x-mw-role')?.value;

  if (role === 'tenant') {
    const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';
    return {
      title: tenantName,
      description: `${tenantName}の公式サイトです。キャスト情報やキャンペーン情報をご覧いただけます。`,
      openGraph: {
        title: tenantName,
        description: `${tenantName}の公式サイトです。`,
        type: 'website',
      },
    };
  }

  return {
    title: 'Kizuna Platform',
  };
}

/**
 * ルートページコンポーネント
 *
 * 役割:
 * - ドメイン（ロール）に応じたルーティングの振り分け。
 * - 適切な模版（Template）の動的読み込み。
 */
export default async function Home() {
  const cookieStore = await cookies();
  const role = cookieStore.get('x-mw-role')?.value;

  // Central ドメイン（管理画面側）の場合、ログイン状態に応じてリダイレクト
  if (role === 'central') {
    const token = cookieStore.get('token')?.value;
    redirect(token ? '/central/dashboard/' : '/login');
  }

  // Tenant ドメイン（店舗フロント側）の場合、模版を表示
  if (role === 'tenant') {
    const templateKey = cookieStore.get('x-mw-tenant-template')?.value || 'default';

    try {
      const { default: TemplateComponent } = await import(
        `@/components/templates/tenant/${templateKey}/page`
      );
      return <TemplateComponent />;
    } catch (e) {
      console.error('模版の読み込みに失敗しました:', e);
      notFound();
    }
  }

  notFound();
}
