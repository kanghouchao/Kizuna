import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';
import { loadTemplatePage } from '@/_pages/store-site';
import { PlatformConsole, resolvePlatformDestination } from '@/entities/user';

/**
 * ページメタデータの生成
 */
export async function generateMetadata(): Promise<Metadata> {
  const cookieStore = await cookies();
  const role = cookieStore.get('x-mw-role')?.value;

  if (role === 'store') {
    const storeName = cookieStore.get('x-mw-store-name')?.value || 'Store';
    return {
      title: storeName,
      description: `${storeName}の公式サイトです。キャスト情報やキャンペーン情報をご覧いただけます。`,
      openGraph: {
        title: storeName,
        description: `${storeName}の公式サイトです。`,
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

  // 平台セッションがあれば、コンソール値に応じて自動遷移する（#324/#398 — cookie 値は central/store）
  const platformConsole = cookieStore.get('platform-role')?.value;
  if (platformConsole) {
    const platformToken = cookieStore.get('token')?.value;
    if (!platformToken) {
      // token 失効後も platform-role cookie が残っていると /store・/platform のガードと無限リダイレクトになるため、先に検出する
      redirect('/platform/login');
    }
    const destination = resolvePlatformDestination(platformConsole as PlatformConsole);
    if (destination === 'platform') {
      redirect('/platform/dashboard/');
    }
    if (destination === 'store') {
      redirect('/store/dashboard/');
    }
  }

  // Platform ドメイン（管理画面側）の場合、ログイン状態に応じてリダイレクト
  if (role === 'platform') {
    const token = cookieStore.get('token')?.value;
    redirect(token ? '/platform/dashboard/' : '/platform/login');
  }

  // Store ドメイン（店舗フロント側）の場合、模版を表示
  if (role === 'store') {
    const templateKey = cookieStore.get('x-mw-store-template')?.value || 'default';

    // 読み込み失敗時は helper 内で default にフォールバックするため、ここでは 404 にしない
    const TemplateComponent = await loadTemplatePage(templateKey);
    return <TemplateComponent />;
  }

  notFound();
}
