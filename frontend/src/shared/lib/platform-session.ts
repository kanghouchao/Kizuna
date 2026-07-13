import Cookies from 'js-cookie';

const PLATFORM_ROLE_COOKIE = 'platform-role';
const PLATFORM_STORE_ID_COOKIE = 'platform-store-id';

/** 平台セッションの cookie 読み書きの唯一の入口。 */
export function getPlatformRole(): string | undefined {
  return Cookies.get(PLATFORM_ROLE_COOKIE);
}

export function getPlatformStoreId(): string | undefined {
  return Cookies.get(PLATFORM_STORE_ID_COOKIE);
}

/** expiresAt（epoch millis）を渡すと token cookie と同じ有効期限を設定し、cookie 間の失効ズレを防ぐ。 */
export function startPlatformSession(role: string, expiresAt?: number): void {
  Cookies.set(PLATFORM_ROLE_COOKIE, role, expiresAt ? { expires: new Date(expiresAt) } : undefined);
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
  return !!getPlatformRole();
}

/** 店舗ロール（STORE_MANAGER/STORE_STAFF）かどうかを判定する。 */
export function isStoreRole(role: string | undefined): boolean {
  return role === 'STORE_MANAGER' || role === 'STORE_STAFF';
}
