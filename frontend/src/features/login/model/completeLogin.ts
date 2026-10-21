import Cookies from 'js-cookie';
import { LoginResponse, authApi } from '@/entities/user';
import { clearSession, startSession, takeMemberReturnPath } from '@/shared/lib';

/** ログイン完了の結果。unsupported は着地先の無い利用者種別で、セッションは破棄済み。 */
export type LoginCompletion = { status: 'ok'; path: string } | { status: 'unsupported' };

/**
 * ログイン応答からセッションを確立し、着地先パスを返す。
 * パスワードログインと LINE ログインが共有する唯一の後処理で、遷移自体は呼び出し元が行う。
 */
export async function completeLogin({
  token,
  expires_at,
}: LoginResponse): Promise<LoginCompletion> {
  // epoch millis を Date に変換する（expires_at をそのまま日数として解釈すると不正な有効期限になる）
  Cookies.set('token', token ?? '', { expires: new Date(expires_at) });

  const me = await authApi.me();

  // 戻り先はどの利用者種別でログインしても取り出して捨てる。会員以外のログインで残すと、同じタブで
  // 後からログインした会員がその戻り先へ運ばれる — 申領画面は開いた時点で伝票を取り込むため、
  // 残った断片は「他人の伝票を無確認で申領する」経路になる。
  //
  // 取り出しは種別の判定より前・{@code me()} の成功より後に置く。1 度きりの取り出しなので、
  // ログインが確定する前に消すと、通信の一時的な失敗でやり直したときには戻り先が既に無い
  // （伝票の生値はこの断片にしか無く、再取得の口が無い）。
  const memberReturnPath = takeMemberReturnPath();

  // 従業員は一つの管理画面へ入り、着地は全員ダッシュボード（集計カードは権限で出し分ける）。
  if (me.user_type === 'STAFF') {
    startSession('admin', expires_at);
    return { status: 'ok', path: '/admin/dashboard/' };
  }

  if (me.user_type === 'CAST') {
    startSession('cast', expires_at);
    return { status: 'ok', path: '/cast/schedule/' };
  }

  if (me.user_type === 'MEMBER') {
    startSession('member', expires_at);
    // 会員ポータル内で弾かれてログインへ回された場合はその画面へ戻す（白名単を通った相対パスのみ）。
    return { status: 'ok', path: memberReturnPath ?? '/member/' };
  }

  // 想定外の user_type: 着地先が無いためセッションを破棄する
  Cookies.remove('token');
  clearSession();
  return { status: 'unsupported' };
}
