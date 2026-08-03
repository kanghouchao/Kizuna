import Cookies from 'js-cookie';
import { LoginResponse, platformAuthApi, resolvePlatformDestination } from '@/entities/user';
import {
  clearPlatformSession,
  startPlatformSession,
  storeEntryPath,
  takeMemberReturnPath,
} from '@/shared/lib';

/** ログイン完了の結果。unsupported は着地先の無い利用者種別で、セッションは破棄済み。 */
export type PlatformLoginCompletion = { status: 'ok'; path: string } | { status: 'unsupported' };

/**
 * ログイン応答から平台セッションを確立し、着地先パスを返す。
 * パスワードログインと LINE ログインが共有する唯一の後処理で、遷移自体は呼び出し元が行う。
 */
export async function completePlatformLogin({
  token,
  expires_at,
}: LoginResponse): Promise<PlatformLoginCompletion> {
  // epoch millis を Date に変換する（expires_at をそのまま日数として解釈すると不正な有効期限になる）
  Cookies.set('token', token ?? '', { expires: new Date(expires_at) });

  const me = await platformAuthApi.me();
  const activeConsole = me.console ?? 'none';
  const destination = resolvePlatformDestination(activeConsole);

  if (destination === 'platform') {
    startPlatformSession(activeConsole, expires_at);
    return { status: 'ok', path: '/platform/dashboard/' };
  }

  if (destination === 'store') {
    // 着地方針（授権店舗の選択とメニュー由来の着地先解決）は StoreEntryPage 一箇所に集約する。
    // ここでは無条件に入口へ渡し、店舗の解決には関与しない。
    startPlatformSession(activeConsole, expires_at);
    return { status: 'ok', path: storeEntryPath() };
  }

  // destination='unsupported'（console='none' — CAST または MEMBER）。両者は console だけでは
  // 区別できないため、既に取得済みの user_type で分岐する。
  if (me.user_type === 'CAST') {
    startPlatformSession('cast', expires_at);
    return { status: 'ok', path: '/cast/schedule/' };
  }

  if (me.user_type === 'MEMBER') {
    startPlatformSession('member', expires_at);
    // 会員ポータル内で弾かれてログインへ回された場合はその画面へ戻す（白名単を通った相対パスのみ）。
    return { status: 'ok', path: takeMemberReturnPath() ?? '/member/' };
  }

  // 想定外の user_type: 着地先が無いためセッションを破棄する
  Cookies.remove('token');
  clearPlatformSession();
  return { status: 'unsupported' };
}
