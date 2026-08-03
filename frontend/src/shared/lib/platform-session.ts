import Cookies from 'js-cookie';

// cookie 名は互換のため platform-role のまま。値は固定ロール名ではなく
// コンソール値（platform / store — /me の console、サーバ側が能力目録から導出）を保存する。
const PLATFORM_ROLE_COOKIE = 'platform-role';
const PLATFORM_STORE_ID_COOKIE = 'platform-store-id';
const MEMBER_RETURN_PATH_COOKIE = 'member-return-path';

/** 平台セッションの cookie 読み書きの唯一の入口。 */
export function getPlatformConsole(): string | undefined {
  return Cookies.get(PLATFORM_ROLE_COOKIE);
}

export function getPlatformStoreId(): string | undefined {
  return Cookies.get(PLATFORM_STORE_ID_COOKIE);
}

/** expiresAt（epoch millis）を渡すと token cookie と同じ有効期限を設定し、cookie 間の失効ズレを防ぐ。 */
export function startPlatformSession(platformConsole: string, expiresAt?: number): void {
  Cookies.set(
    PLATFORM_ROLE_COOKIE,
    platformConsole,
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
export function isStoreConsole(platformConsole: string | undefined): boolean {
  return platformConsole === 'store';
}

/**
 * 会員ポータル内の相対パスだけを許す白名単。
 *
 * <p>ログイン後の遷移先を利用者側の値から決めるため、素通しにすると外部サイトへ飛ばせてしまう。
 * `/member/` 配下であること、スキームや `//host` の形を取らないこと、クエリを含めても記号が限られることを
 * すべて満たすものだけを通す（判定は保存時と取り出し時の両方で行う）。
 */
export function isSafeMemberReturnPath(value: string | undefined | null): value is string {
  if (!value) {
    return false;
  }
  if (!value.startsWith('/member/') || value.startsWith('//')) {
    return false;
  }
  if (value.includes('\\') || value.includes(':')) {
    return false;
  }
  return /^\/member\/[A-Za-z0-9/_-]*(\?[A-Za-z0-9=&._%-]*)?$/.test(value);
}

/** ログイン後に戻る会員ポータル内のパスを覚える。安全でない値は黙って捨てる。 */
export function rememberMemberReturnPath(value: string): void {
  if (!isSafeMemberReturnPath(value)) {
    return;
  }
  Cookies.set(MEMBER_RETURN_PATH_COOKIE, value);
}

/** 覚えた戻り先を 1 度だけ取り出す（取り出しと同時に消す）。無い・安全でない場合は null。 */
export function takeMemberReturnPath(): string | null {
  const value = Cookies.get(MEMBER_RETURN_PATH_COOKIE);
  Cookies.remove(MEMBER_RETURN_PATH_COOKIE);
  return isSafeMemberReturnPath(value) ? value : null;
}
