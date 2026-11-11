import Cookies from 'js-cookie';

// session-kind cookie の値。従業員は単一の管理画面 admin に入り、キャスト・会員はそれぞれの portal に入る。
export const SESSION_KINDS = ['admin', 'cast', 'member'] as const;
export type SessionKind = (typeof SESSION_KINDS)[number];

const SESSION_KIND_COOKIE = 'session-kind';

/** セッションの cookie 読み書きの唯一の入口。種別でない値（手で書き替えられた cookie など）は無いものとして扱う。 */
export function getSessionKind(): SessionKind | undefined {
  const value = Cookies.get(SESSION_KIND_COOKIE);
  return SESSION_KINDS.find(kind => kind === value);
}

/** expiresAt（epoch millis）にはリフレッシュトークンの期限を渡し、ログインが続く間だけ session-kind を残す。 */
export function startSession(sessionKind: SessionKind, expiresAt?: number): void {
  Cookies.set(
    SESSION_KIND_COOKIE,
    sessionKind,
    expiresAt ? { expires: new Date(expiresAt) } : undefined
  );
}

export function clearSession(): void {
  Cookies.remove(SESSION_KIND_COOKIE);
}
