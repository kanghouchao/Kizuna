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

export function clearSessionToken(): void {
  current = null;
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
export function broadcastLogout(userId: number): void {
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
