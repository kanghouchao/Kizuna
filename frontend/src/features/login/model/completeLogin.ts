import { LoginResponse, MeResponse, authApi } from '@/entities/user';
import { clearSessionToken, storeSessionToken } from '@/shared/api';
import { clearSession, startSession, takeMemberReturnPath } from '@/shared/lib';

/** ログイン完了の結果。unsupported は着地先の無い利用者種別で、セッションは破棄済み。 */
export type LoginCompletion = { status: 'ok'; path: string } | { status: 'unsupported' };

/**
 * ログイン応答のトークンをメモリへ置き、着地先パスを返す。
 * パスワードログインと LINE ログインが共有する唯一の後処理で、遷移自体は呼び出し元が行う。
 */
export async function completeLogin(response: LoginResponse): Promise<LoginCompletion> {
  storeSessionToken(response);
  return landSession(await authApi.me(), response.refresh_expires_at);
}

/**
 * トークンを持った後の着地先を GET /me の利用者種別で決め、session-kind を始める（期限はリフレッシュトークンに
 * 揃える）。ログイン画面を開いた時点でリフレッシュに成功した場合も、ここから着地する。
 */
export function landSession(me: MeResponse, refreshExpiresAt: number): LoginCompletion {
  // 戻り先はどの利用者種別でログインしても取り出して捨てる。会員以外のログインで残すと、同じタブで
  // 後からログインした会員がその戻り先へ運ばれる — 申領画面は開いた時点で伝票を取り込むため、
  // 残った断片は「他人の伝票を無確認で申領する」経路になる。
  //
  // 取り出しは種別の判定より前・{@code me()} の成功より後（呼び出し元が取得済み）に置く。1 度きりの取り出しなので、
  // ログインが確定する前に消すと、通信の一時的な失敗でやり直したときには戻り先が既に無い
  // （伝票の生値はこの断片にしか無く、再取得の口が無い）。
  const memberReturnPath = takeMemberReturnPath();

  // 従業員は一つの管理画面へ入り、着地は全員ダッシュボード（集計カードは権限で出し分ける）。
  if (me.user_type === 'STAFF') {
    startSession('admin', refreshExpiresAt);
    return { status: 'ok', path: '/admin/dashboard/' };
  }

  if (me.user_type === 'CAST') {
    startSession('cast', refreshExpiresAt);
    return { status: 'ok', path: '/cast/schedule/' };
  }

  if (me.user_type === 'MEMBER') {
    startSession('member', refreshExpiresAt);
    // 会員ポータル内で弾かれてログインへ回された場合はその画面へ戻す（白名単を通った相対パスのみ）。
    return { status: 'ok', path: memberReturnPath ?? '/member/' };
  }

  // 想定外の user_type: 着地先が無いためセッションを破棄する
  clearSessionToken();
  clearSession();
  return { status: 'unsupported' };
}
