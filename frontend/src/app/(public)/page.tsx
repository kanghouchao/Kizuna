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
  // session-kind の期限はリフレッシュトークンに揃えてあり、ログインの継続はその先の画面がリフレッシュで確かめる。
  const sessionKind = cookieStore.get('session-kind')?.value;
  if (sessionKind) {
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

  // アプリドメイン（管理画面側）でセッション種別が無ければログイン画面へ（ログイン済みならそこで着地先へ移る）
  if (host.kind === 'app') {
    redirect('/auth/login');
  }

  // 店舗ドメインなら店舗サイトのトップを描く
  return <StoreSitePage page="page" />;
}
