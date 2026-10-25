import { serverClient } from '@/shared/api/index.server';

export type InitialSetupState = 'completed' | 'required';

/**
 * サーバーが完了済みと答えたときだけ completed を返す。取得の失敗は required に倒して画面を出す —
 * 完了後の登録は送信先が 404 で拒むため、ここで閉じなくても先着の管理者は守られる。
 */
export async function resolveInitialSetupState(): Promise<InitialSetupState> {
  try {
    const { required } = await serverClient.get<{ required: boolean }>('/auth/initial-setup', {
      cache: 'no-store',
    });
    return required === false ? 'completed' : 'required';
  } catch (error) {
    console.error('初期設定の要否の取得に失敗しました:', error);
    return 'required';
  }
}
