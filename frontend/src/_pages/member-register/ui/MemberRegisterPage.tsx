import Link from 'next/link';
import { MemberRegisterForm } from '@/features/member-register';
import { AuthLayout } from '@/shared/ui';

/**
 * 新規会員登録ページ（サーバーコンポーネント）。統一ログイン画面の「新規会員登録」から遷移する。
 * 登録入口から生まれる身分は会員のみ（キャスト = 招待制、スタッフ = 管理者作成）。
 */
export function MemberRegisterPage() {
  return (
    <AuthLayout title="新規会員登録" subtitle="メールアドレスとパスワードで会員登録できます">
      <MemberRegisterForm />
      <p className="mt-6 text-center text-xs text-[#9a958e]">
        すでにアカウントをお持ちの方は{' '}
        <Link
          href="/platform/login"
          className="text-[#7c3aed] hover:underline focus:underline focus:outline-none"
        >
          ログイン
        </Link>
      </p>
    </AuthLayout>
  );
}
