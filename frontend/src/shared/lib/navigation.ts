// 遷移の実行部を差し替え可能にしたナビゲーション（テストでは navigator を注入する）
import { SESSION_KINDS, type SessionKind } from './session';

/**
 * ログイン後の戻り先は、ログイン画面の `?next=<パス>` と URL の断片で持ち越す。断片は伝票トークンのように
 * 所持そのものが証明になる値を運ぶため、クエリにもサーバへ送られる置き場にも載せない。
 */
const NEXT_PREFIXES: Record<SessionKind, readonly string[]> = {
  admin: ['/admin/', '/store/'],
  cast: ['/cast/'],
  member: ['/member/'],
};

// 利用者側の値を遷移先にするため許可リストで通す。`.` をパスに許すと `/member/../admin/` で接頭辞の判定を越えられる。
const SAFE_PATH = /^\/[A-Za-z0-9/_-]*(\?[A-Za-z0-9=&._%+-]*)?$/;

function isSafeNextPath(kind: SessionKind, path: string): boolean {
  return (
    SAFE_PATH.test(path) &&
    !path.includes('//') &&
    NEXT_PREFIXES[kind].some(prefix => path.startsWith(prefix))
  );
}

function splitFragment(target: string): [path: string, fragment: string] {
  const index = target.indexOf('#');
  return index < 0 ? [target, ''] : [target.slice(0, index), target.slice(index)];
}

/** 戻り先（`パス#断片`）がその本人種別の自サイト内の相対パスなら返し、そうでなければ null。断片は origin を変えられない。 */
export function resolveNext(kind: SessionKind, target: string | undefined): string | null {
  if (!target) return null;
  return isSafeNextPath(kind, splitFragment(target)[0]) ? target : null;
}

/** 認証画面のパスに戻り先を載せる。パスは next に符号化し、断片は URL の断片で持ち越す。 */
export function withNext(
  base: string,
  target: string | undefined,
  { reason }: { reason?: string } = {}
): string {
  const params = new URLSearchParams();
  if (reason) params.set('reason', reason);
  const [path, fragment] = target ? splitFragment(target) : ['', ''];
  if (path) params.set('next', path);
  const query = params.toString();
  return `${base}${query ? `?${query}` : ''}${path ? fragment : ''}`;
}

/** 今の画面を戻り先にする。どの本人種別の画面でもなければ持たない（コールバックのクエリを写さない）。 */
export function currentReturnTarget(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  const { pathname, search, hash } = window.location;
  const path = `${pathname}${search}`;
  return SESSION_KINDS.some(kind => isSafeNextPath(kind, path)) ? `${path}${hash}` : undefined;
}

/** 認証画面（ログイン・会員登録）の URL が持ち越している戻り先。検証は着地で本人種別ごとに行う。 */
export function loginReturnTarget(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  const next = new URLSearchParams(window.location.search).get('next');
  return next ? `${next}${window.location.hash}` : undefined;
}

type NavigatorFn = (url: string) => void;

function defaultNavigator(url: string) {
  if (typeof window !== 'undefined') {
    try {
      window.location.assign(url);
    } catch {
      // jsdom などページ遷移を実装しない環境では握りつぶす
    }
  }
}

let navigatorFn: NavigatorFn = defaultNavigator;

/**
 * ログイン画面のパス。理由を渡すと、着地したログイン画面がその理由を名乗る
 * （許可リストの理由コード。知らない値は着地側が黙って捨てる）。戻り先は next と断片で載せる。
 *
 * 組み立てをここ一箇所に閉じるのは、差し戻す経路が全画面遷移（この下）と
 * クライアント遷移（AuthContext の logout）の二つに分かれているため。
 */
export function loginPath({ reason, next }: { reason?: string; next?: string } = {}) {
  return withNext('/auth/login', next, { reason });
}

/** ログイン画面へ差し戻す。returnHere なら今の画面を戻り先として預け、ログイン後にここへ戻す。 */
export function redirectToLogin({
  reason,
  returnHere = false,
}: { reason?: string; returnHere?: boolean } = {}) {
  if (typeof window !== 'undefined') {
    try {
      if (!window.location.pathname.includes('/login')) {
        navigatorFn(loginPath({ reason, next: returnHere ? currentReturnTarget() : undefined }));
      }
    } catch {
      // pathname の読み取りが例外になる環境では何もしない
    }
  }
}

export function __setNavigatorForTests(fn: NavigatorFn) {
  navigatorFn = fn;
}

export function __resetNavigator() {
  navigatorFn = defaultNavigator;
}

export default redirectToLogin;
