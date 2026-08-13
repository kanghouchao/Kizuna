import Cookies from 'js-cookie';

// cookie 名は互換のため platform-role のまま。値は固定ロール名ではなく
// コンソール値（platform / store — /me の console、サーバ側が能力目録から導出）を保存する。
const PLATFORM_ROLE_COOKIE = 'platform-role';
const PLATFORM_STORE_ID_COOKIE = 'platform-store-id';
// proxy 側（routeGuard）はサーバ応答の Set-Cookie で書き、クライアント側は js-cookie で読み書きする。
export const MEMBER_RETURN_PATH_COOKIE = 'member-return-path';

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

/**
 * 戻り先のフラグメントの置き場。cookie ではなく sessionStorage なのは、フラグメントが伝票トークン
 * （所持そのものが証明になるクレデンシャル）を運ぶため — cookie は毎要求サーバへ送られるので、
 * サーバへ渡らないことを担保していたフラグメントの性質がそこで失われる。
 */
const MEMBER_RETURN_FRAGMENT_KEY = 'member-return-fragment';

/**
 * 断片の読み書き。sessionStorage は参照そのものも各操作も投げうる（プライベートモード・容量超過）ため、
 * 呼出側へは決して例外を出さない — 戻り先の復帰は付随的な便宜であり、失敗して困るのは「元の画面へ
 * 戻れない」ことだけである。ここで投げると、呼び元（シェルの差し戻し）が途中で止まり、未認証の利用者が
 * ログインへも進めないまま読み込み中の画面に取り残される。
 */
function readFragment(): string | null {
  try {
    return typeof window === 'undefined'
      ? null
      : window.sessionStorage.getItem(MEMBER_RETURN_FRAGMENT_KEY);
  } catch {
    return null;
  }
}

function writeFragment(fragment: string | null): void {
  try {
    if (typeof window === 'undefined') {
      return;
    }
    if (fragment === null) {
      window.sessionStorage.removeItem(MEMBER_RETURN_FRAGMENT_KEY);
      return;
    }
    window.sessionStorage.setItem(MEMBER_RETURN_FRAGMENT_KEY, fragment);
  } catch {
    // 書けなくても戻り先の断片が無いだけ（＝QR を読み直せば済む）。消せなかった場合も、
    // 断片は戻り先 cookie が通ったときにしか使われないので、単体では遷移先にならない。
  }
}

/**
 * ログイン後に戻る会員ポータル内のパスを覚える。安全でない値は黙って捨てる。
 *
 * @param fragment 戻り先のフラグメント（`window.location.hash` をそのまま）。QR から開いた申領画面のように、
 *     画面が読み取る値がフラグメントにしか無い経路のためにログイン往復で持ち越す。省略・空なら覚えていた分を捨てる
 *     — 別の画面から入り直したときに前の断片が付いて回らないようにする
 */
export function rememberMemberReturnPath(value: string, fragment?: string): void {
  writeFragment(null);
  if (!isSafeMemberReturnPath(value)) {
    return;
  }
  Cookies.set(MEMBER_RETURN_PATH_COOKIE, value);
  // `#` で始まるものだけを断片として扱う。連結先はそのまま遷移に渡るため、断片であることが
  // 形から明らかな値以外は載せない（`#` 以降はホストにもパスにもなり得ない）。
  if (fragment?.startsWith('#')) {
    writeFragment(fragment);
  }
}

/** 覚えた戻り先を 1 度だけ取り出す（取り出しと同時に消す）。無い・安全でない場合は null。 */
export function takeMemberReturnPath(): string | null {
  const value = Cookies.get(MEMBER_RETURN_PATH_COOKIE);
  Cookies.remove(MEMBER_RETURN_PATH_COOKIE);
  const fragment = readFragment();
  writeFragment(null);
  if (!isSafeMemberReturnPath(value)) {
    return null;
  }
  return fragment?.startsWith('#') ? `${value}${fragment}` : value;
}
