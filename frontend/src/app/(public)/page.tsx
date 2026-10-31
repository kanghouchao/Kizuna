import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';
import { StoreSitePage, storeSiteMetadata } from '@/_pages/store-site';
import { resolveHost } from '@/shared/api/index.server';

export async function generateMetadata(): Promise<Metadata> {
  return storeSiteMetadata();
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
  // 未登録の Host（削除した店舗を含む）は、セッションの有無に依らず 404 にする
  const host = await resolveHost();
  if (host.kind === 'unknown') {
    notFound();
  }

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
  if (host.kind === 'app') {
    const token = cookieStore.get('token')?.value;
    redirect(token ? '/admin/dashboard/' : '/auth/login');
  }

  // 店舗ドメインなら店舗サイトのトップを描く
  return <StoreSitePage page="page" />;
}
