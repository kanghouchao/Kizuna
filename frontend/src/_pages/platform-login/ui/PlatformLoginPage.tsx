import { PlatformLoginForm } from '@/features/platform-login';
import { AuthLayout } from '@/shared/ui';

/**
 * 統一ログインページ（サーバーコンポーネント）
 *
 * HQ 管理者・店長・スタッフを問わず単一の入口として機能し、ログイン成功後は
 * ロールに応じて自動的に適切なコンソールへ遷移する（#324）。
 */
export default function PlatformLoginPage() {
  return (
    <AuthLayout title="統一ログイン" subtitle="メールアドレスとパスワードでログインしてください">
      <PlatformLoginForm />
      {/* フッター */}
      <p className="auth-footer mt-12 text-center">ご不明点はKIZUNAサポートまでご連絡ください</p>
    </AuthLayout>
  );
}
