import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';
import { loadTemplatePage } from '@/_pages/store-site';

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

  return {};
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

  // セッションがあれば、セッション種別に応じて自動遷移する（cookie 値は admin/cast/member）
  const sessionKind = cookieStore.get('session-kind')?.value;
  if (sessionKind) {
    const sessionToken = cookieStore.get('token')?.value;
    if (!sessionToken) {
      // token 失効後も session-kind cookie が残っていると /store・/admin のガードと無限リダイレクトになるため、先に検出する
      redirect('/auth/login');
    }
    if (sessionKind === 'admin') {
      redirect('/admin/dashboard/');
    }
    if (sessionKind === 'cast') {
      redirect('/cast/schedule/');
    }
    if (sessionKind === 'member') {
      redirect('/member/');
    }
  }

  // アプリドメイン（管理画面側）の場合、ログイン状態に応じてリダイレクト
  if (role === 'app') {
    const token = cookieStore.get('token')?.value;
    redirect(token ? '/admin/dashboard/' : '/auth/login');
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
