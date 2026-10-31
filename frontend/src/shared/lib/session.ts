import Cookies from 'js-cookie';

// 値はセッション種別（admin / cast / member — ログイン時に /me の user_type から決める）。
const SESSION_KIND_COOKIE = 'session-kind';
const CURRENT_STORE_ID_COOKIE = 'current-store-id';
const MEMBER_RETURN_PATH_COOKIE = 'member-return-path';

/** セッションの cookie 読み書きの唯一の入口。 */
export function getSessionKind(): string | undefined {
  return Cookies.get(SESSION_KIND_COOKIE);
}

export function getCurrentStoreId(): string | undefined {
  return Cookies.get(CURRENT_STORE_ID_COOKIE);
}

/** expiresAt（epoch millis）にはリフレッシュトークンの期限を渡し、ログインが続く間だけ session-kind を残す。 */
export function startSession(sessionKind: string, expiresAt?: number): void {
  Cookies.set(
    SESSION_KIND_COOKIE,
    sessionKind,
    expiresAt ? { expires: new Date(expiresAt) } : undefined
  );
}

export function setCurrentStore(id: number | string, expiresAt?: number): void {
  Cookies.set(
    CURRENT_STORE_ID_COOKIE,
    String(id),
    expiresAt ? { expires: new Date(expiresAt) } : undefined
  );
}

export function clearSession(): void {
  try {
    for (let i = sessionStorage.length - 1; i >= 0; i--) {
      const key = sessionStorage.key(i);
      if (key?.startsWith('pending-operation:')) sessionStorage.removeItem(key);
    }
  } catch {
    // 保存領域が利用不能でもログアウトは完了させる。
  }
  Cookies.remove(SESSION_KIND_COOKIE);
  Cookies.remove(CURRENT_STORE_ID_COOKIE);
}

/** session-kind cookie の存在でセッションかどうかを判定する（fail-closed ではなく単なる存在確認）。 */
export function hasSession(): boolean {
  return !!getSessionKind();
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
 * 断片つきの戻り先（`パス#断片`）の置き場。cookie ではなく sessionStorage なのは、断片が伝票トークン
 * （所持そのものが証明になるクレデンシャル）を運ぶため — cookie は毎要求サーバへ送られるので、
 * サーバへ渡らないことを担保していた断片の性質がそこで失われる。
 *
 * <p>断片だけでなく<b>パスごと</b>持つ。cookie はタブ間で共有される一方この置き場はタブ内に閉じるため、
 * 別タブの差し戻しが cookie を書き替えた後だと、断片だけでは元のタブの伝票トークンが無関係なパスへ付く。
 * 組で持って一致を確かめれば、その組み合わせは成立しない。パスの白名単は `#` を通さないので、最初の
 * `#` が組の区切りとして曖昧にならない。
 */
const MEMBER_RETURN_TARGET_KEY = 'member-return-target';

/**
 * 戻り先の組の読み書き。sessionStorage は参照そのものも各操作も投げうる（プライベートモード・容量超過）ため、
 * 呼出側へは決して例外を出さない — 戻り先の復帰は付随的な便宜であり、失敗して困るのは「元の画面へ
 * 戻れない」ことだけである。ここで投げると、呼び元（シェルの差し戻し）が途中で止まり、未認証の利用者が
 * ログインへも進めないまま読み込み中の画面に取り残される。
 */
function readTarget(): string | null {
  try {
    return typeof window === 'undefined'
      ? null
      : window.sessionStorage.getItem(MEMBER_RETURN_TARGET_KEY);
  } catch {
    return null;
  }
}

function writeTarget(target: string | null): void {
  try {
    if (typeof window === 'undefined') {
      return;
    }
    if (target === null) {
      window.sessionStorage.removeItem(MEMBER_RETURN_TARGET_KEY);
      return;
    }
    window.sessionStorage.setItem(MEMBER_RETURN_TARGET_KEY, target);
  } catch {
    // 書けなくても戻り先の断片が無いだけ（＝QR を読み直せば済む）。消せなかった場合も、
    // 組は cookie のパスと一致したときにしか使われないので、単体では遷移先にならない。
  }
}

/**
 * ログイン後に戻る会員ポータル内のパスを覚える。安全でない値は黙って捨てる。
 *
 * <p>安全でない戻り先の要求では<b>何も触らない</b>。cookie と断片は対で 1 つの戻り先であり、片方だけ消すと
 * 「戻り先はあるがトークンが無い」着地になる — 401 の差し戻しは会員ポータルの外（LINE コールバック等）からも
 * 起き、そこでは覚える値が白名単を通らない。
 *
 * @param fragment 戻り先のフラグメント（`window.location.hash` をそのまま）。QR から開いた申領画面のように、
 *     画面が読み取る値がフラグメントにしか無い経路のためにログイン往復で持ち越す。省略・空なら覚えていた分を捨てる
 *     — 別の画面から入り直したときに前の断片が付いて回らないようにする
 */
export function rememberMemberReturnPath(value: string, fragment?: string): void {
  if (!isSafeMemberReturnPath(value)) {
    return;
  }
  Cookies.set(MEMBER_RETURN_PATH_COOKIE, value);
  // 組は cookie と必ず同時に書き替える。`#` で始まるものだけを断片として扱うのは、連結先が
  // そのまま遷移に渡るため — 断片であることが形から明らかな値以外は載せない
  // （`#` 以降はホストにもパスにもなり得ない）。
  writeTarget(fragment?.startsWith('#') ? `${value}${fragment}` : null);
}

/** 会員の戻り先が預けられているか。取り出さずに見るだけ（ログイン画面の自動着地の判断に使う）。 */
export function hasMemberReturnPath(): boolean {
  return isSafeMemberReturnPath(Cookies.get(MEMBER_RETURN_PATH_COOKIE));
}

/**
 * 覚えた戻り先を 1 度だけ取り出す（取り出しと同時に消す）。無い・安全でない場合は null。
 *
 * <p>断片を添えるのは、預けた組が cookie のパスと一致するときだけ。別タブの差し戻しが cookie を
 * 書き替えていた場合はパスが食い違うため、このタブの伝票トークンが無関係な画面へ付いて回らない。
 */
export function takeMemberReturnPath(): string | null {
  const value = Cookies.get(MEMBER_RETURN_PATH_COOKIE);
  Cookies.remove(MEMBER_RETURN_PATH_COOKIE);
  const target = readTarget();
  writeTarget(null);
  if (!isSafeMemberReturnPath(value)) {
    return null;
  }
  return target?.startsWith(`${value}#`) ? target : value;
}
