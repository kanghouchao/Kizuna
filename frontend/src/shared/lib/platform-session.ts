import Cookies from 'js-cookie';

// cookie 名は互換のため platform-role のまま。#398 以降、値は固定ロール名ではなく
// コンソール値（central / store — /me の console、サーバ側が能力目録から導出）を保存する。
const PLATFORM_ROLE_COOKIE = 'platform-role';
const PLATFORM_STORE_ID_COOKIE = 'platform-store-id';

/** 平台セッションの cookie 読み書きの唯一の入口。 */
export function getPlatformConsole(): string | undefined {
  return Cookies.get(PLATFORM_ROLE_COOKIE);
}

export function getPlatformStoreId(): string | undefined {
  return Cookies.get(PLATFORM_STORE_ID_COOKIE);
}

/** expiresAt（epoch millis）を渡すと token cookie と同じ有効期限を設定し、cookie 間の失効ズレを防ぐ。 */
export function startPlatformSession(console: string, expiresAt?: number): void {
  Cookies.set(
    PLATFORM_ROLE_COOKIE,
    console,
    expiresAt ? { expires: new Date(expiresAt) } : undefined
  );
}

export function setPlatformStore(id: number | string, expiresAt?: number): void {
  Cookies.set(
    PLATFORM_STORE_ID_COOKIE,
    String(id),
    expiresAt ? { expires: new Date(expiresAt) } : undefined
  );
}

export function clearPlatformSession(): void {
  Cookies.remove(PLATFORM_ROLE_COOKIE);
  Cookies.remove(PLATFORM_STORE_ID_COOKIE);
}

/** platform-role cookie の存在で平台セッションかどうかを判定する（fail-closed ではなく単なる存在確認）。 */
export function isPlatformSession(): boolean {
  return !!getPlatformConsole();
}

/** 店舗コンソールかどうか。旧形式（ロール名）の cookie 値は false になる（fail-closed — 要再ログイン）。 */
export function isStoreConsole(console: string | undefined): boolean {
  return console === 'store';
}
