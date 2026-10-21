import Link from 'next/link';
import { LineLoginButton } from '@/features/line-auth';
import { PlatformLoginForm } from '@/features/platform-login';
import { AuthLayout } from '@/shared/ui';
import { resolveLoginReason } from '../model/loginReason';

interface PlatformLoginPageProps {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

/**
 * 統一ログインページ（サーバーコンポーネント）
 *
 * HQ 管理者・店長・スタッフを問わず単一の入口として機能し、ログイン成功後は
 * ロールに応じて自動的に適切なコンソールへ遷移する。
 *
 * 送り返されて来た利用者には、その理由を query の `reason` から引き当てて名乗る。
 * 引き当ては純関数が持ち、ここは戻り値を描くだけ — query の文字列は決して描かない。
 */
export default async function PlatformLoginPage({ searchParams }: PlatformLoginPageProps) {
  const { reason } = await searchParams;
  const notice = resolveLoginReason(reason);

  return (
    <AuthLayout title="統一ログイン" subtitle="メールアドレスとパスワードでログインしてください">
      {/* 領域内エラー態と違い role="alert" は要らない。この文言は最初の描画に含まれており、
          読み上げは画面を頭から読むときに拾う（後から差し込まれる知らせではない）。 */}
      {notice && <div className="auth-alert auth-alert--neutral mb-6">{notice}</div>}
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
