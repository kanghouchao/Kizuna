import Link from 'next/link';
import { LineLoginButton } from '@/features/line-auth';
import { PlatformLoginForm } from '@/features/platform-login';
import { AuthLayout } from '@/shared/ui';

/**
 * 統一ログインページ（サーバーコンポーネント）
 *
 * HQ 管理者・店長・スタッフを問わず単一の入口として機能し、ログイン成功後は
 * ロールに応じて自動的に適切なコンソールへ遷移する。
 */
export default function PlatformLoginPage() {
  return (
    <AuthLayout title="統一ログイン" subtitle="メールアドレスとパスワードでログインしてください">
      <PlatformLoginForm />
      {/* LINE ログインの入口（公開設定が無効なら何も描画されない） */}
      <LineLoginButton />
      {/* 新規会員登録の入口（登録入口から生まれる身分は会員のみ） */}
      <p className="mt-6 text-center text-xs text-[#9a958e]">
        はじめての方は{' '}
        <Link
          href="/platform/register"
          className="text-[#7c3aed] hover:underline focus:underline focus:outline-none"
        >
          新規会員登録
        </Link>
      </p>
      {/* フッター */}
      <p className="auth-footer mt-12 text-center">ご不明点はKIZUNAサポートまでご連絡ください</p>
    </AuthLayout>
  );
}
