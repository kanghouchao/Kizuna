import { cookies } from 'next/headers';
import { LoginForm } from '@/features/auth-login';
import { AuthLayout } from '@/shared/ui';

/**
 * Cookie 値のデコード。js-cookie の読み取り互換（decodeURIComponent + 失敗時は原文）。
 * proxy が response.cookies.set で書き込む際にエンコードされるため、
 * デコードしないと日本語店名がエンコードされたまま表示される。
 */
function decodeCookieValue(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

/**
 * ログインページ（サーバーコンポーネント）
 *
 * タイトル文言はサーバー側で Cookie（x-mw-role / x-mw-tenant-name）から確定させる。
 * 以前はクライアントで document.cookie と window.location を読んでいたため、
 * SSR 出力と初回 hydration の内容が食い違い React error #418 が発生、
 * フォームが再マウントされて入力値が消えていた（issue #228）。
 */
export default async function LoginPage() {
  const cookieStore = await cookies();
  const isTenant = cookieStore.get('x-mw-role')?.value === 'tenant';
  const rawTenantName = cookieStore.get('x-mw-tenant-name')?.value;
  const tenantName = rawTenantName ? decodeCookieValue(rawTenantName) : undefined;

  const pageTitle = isTenant ? tenantName || '店舗ログイン' : '管理者ログイン';
  const pageSubtitle = isTenant
    ? '店舗アカウントでログインしてください'
    : 'プラットフォーム管理者アカウントでログインしてください';

  return (
    <AuthLayout title={pageTitle} subtitle={pageSubtitle}>
      <LoginForm />
      {/* フッター */}
      <p className="auth-footer mt-12 text-center">ご不明点はKIZUNAサポートまでご連絡ください</p>
    </AuthLayout>
  );
}
