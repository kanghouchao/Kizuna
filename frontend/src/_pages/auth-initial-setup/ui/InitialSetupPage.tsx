import { redirect } from 'next/navigation';
import { AuthLayout } from '@/shared/ui';
import { resolveInitialSetupState } from '../api/initialSetupState';
import { InitialSetupForm } from './InitialSetupForm';

/** 最初の管理者を登録する初期設定ページ（サーバーコンポーネント）。完了済みならログイン画面へ送る。 */
export async function InitialSetupPage() {
  // redirect は例外で抜けるため、取得の try/catch の外で呼ぶ（中で呼ぶと catch が飲み込む）
  if ((await resolveInitialSetupState()) === 'completed') {
    redirect('/auth/login');
  }

  return (
    <AuthLayout title="初期設定" subtitle="最初の管理者アカウントを登録してください">
      <InitialSetupForm />
    </AuthLayout>
  );
}
