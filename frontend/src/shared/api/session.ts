// 桶（@/shared/lib）経由だと useOperation が本モジュールを読み、初期化の循環になる
import { forgetAll } from '@/shared/lib/operation-store';
import { resolveNext } from '@/shared/lib/navigation';
import { clearSession, getSessionKind, type SessionKind, startSession } from '@/shared/lib/session';
import type { TokenResponse } from './types';

/**
 * アクセストークンとその利用者 ID の置き場。タブのメモリ（文書の生存期間）だけに持ち、cookie・
 * localStorage・sessionStorage には書かない — 全タブで共有する置き場に置くと、別タブのログインで
 * 要求の本人だけが差し替わり、画面の本人（/me）と食い違う。継続ログインはリフレッシュ cookie が担う。
 */
let current: { token: string; userId: number } | null = null;

export function storeSessionToken({ token, user_id }: TokenResponse): void {
  current = { token, userId: user_id };
}

export function getAccessToken(): string | undefined {
  return current?.token;
}

export function getSessionUserId(): number | undefined {
  return current?.userId;
}

export type { SessionKind };

/**
 * トークンを持った後のセッションを始め、着地パスを返す。session-kind の期限はリフレッシュトークンに揃える。
 * 戻り先（ログイン画面の next と断片）はこの本人種別の自サイト内の相対パスのときだけ使い、それ以外は既定の着地へ進む。
 */
export function landSession(kind: SessionKind, refreshExpiresAt: number, next?: string): string {
  startSession(kind, refreshExpiresAt);
  const returnTo = resolveNext(kind, next);
  if (returnTo) return returnTo;
  // 従業員は一つの管理画面へ入り、着地は全員ダッシュボード（集計カードは権限で出し分ける）。
  if (kind === 'admin') return '/admin/dashboard/';
  if (kind === 'cast') return '/cast/';
  return '/member/';
}

/** リフレッシュの成功で session-kind の期限を延ばす（着地ではない）。 */
export function extendSession(refreshExpiresAt: number): void {
  const kind = getSessionKind();
  if (kind) startSession(kind, refreshExpiresAt);
}

export type EndReason =
  | 'logout'
  | 'unsupported'
  | 'expired'
  | 'other-tab'
  | 'credential-changed'
  | 'relogin'
  | 'resume-failed';

interface EndScope {
  /** session-kind / current-store cookie */
  cookies: boolean;
  /** 再読込を越えて保持した結果不明の操作 */
  operations: boolean;
  broadcast: boolean;
}

/**
 * 終了理由ごとに消す範囲。トークンはどの理由でも消す。結果不明の操作は再送が決着するまで忘れない
 * （GLOSSARY「操作の受領」）ので、消すのはセッションごと捨てる理由だけ。
 */
const END_SCOPES: Record<EndReason, EndScope> = {
  logout: { cookies: true, operations: true, broadcast: true },
  unsupported: { cookies: true, operations: true, broadcast: false },
  expired: { cookies: false, operations: false, broadcast: false },
  'other-tab': { cookies: false, operations: false, broadcast: false },
  'credential-changed': { cookies: false, operations: false, broadcast: false },
  relogin: { cookies: true, operations: true, broadcast: false },
  'resume-failed': { cookies: false, operations: false, broadcast: false },
};

/**
 * セッションを終える。消す範囲は END_SCOPES で決まり、画面遷移は呼出側が行う。放送の宛先 userId は既定で
 * メモリの利用者。ログアウトは端点を待つ間に別の要求の 401 がトークンを消しうるので、呼ぶ前に控えた値を渡す。
 */
export function endSession(reason: EndReason, userId = current?.userId): void {
  const scope = END_SCOPES[reason];
  current = null;
  if (scope.cookies) clearSession();
  if (scope.operations) forgetAll();
  if (scope.broadcast && userId !== undefined) broadcastLogout(userId);
}

/**
 * リフレッシュの失敗のうち、自動では遷移せず利用者の操作を待つもの。credential_changed は再ログインを、
 * conflict（リフレッシュ cookie が別のアカウントのもの）は再読込を促す。入力中の内容を黙って捨てない。
 */
export type SessionAlert = 'credential_changed' | 'conflict';

let alert: SessionAlert | null = null;
const alertListeners = new Set<() => void>();

export function getSessionAlert(): SessionAlert | null {
  return alert;
}

export function subscribeSessionAlert(listener: () => void): () => void {
  alertListeners.add(listener);
  return () => {
    alertListeners.delete(listener);
  };
}

export function setSessionAlert(value: SessionAlert | null): void {
  alert = value;
  alertListeners.forEach(listener => listener());
}

const LOGOUT_CHANNEL = 'kizuna-logout';

/**
 * 同じブラウザの他のタブへログアウトを知らせる。送信元には届かないので、送信元は自分で消す。
 * 照合は利用者 ID で行う（フロントは JWT を解読しないので sid は使えない）。
 */
function broadcastLogout(userId: number): void {
  if (typeof BroadcastChannel === 'undefined') return;
  const channel = new BroadcastChannel(LOGOUT_CHANNEL);
  channel.postMessage({ user_id: userId });
  channel.close();
}

/** 他のタブのログアウトを受け取る。メモリの利用者と同じ利用者のときだけ listener を呼ぶ。 */
export function subscribeLogout(listener: () => void): () => void {
  if (typeof BroadcastChannel === 'undefined') return () => {};
  const channel = new BroadcastChannel(LOGOUT_CHANNEL);
  channel.onmessage = (event: MessageEvent<{ user_id?: unknown }>) => {
    const userId = getSessionUserId();
    if (userId !== undefined && event.data?.user_id === userId) listener();
  };
  return () => channel.close();
}
