/**
 * セッション不要の auth ルート（ログインフォーム・招待受諾・会員登録・LINE コールバック）。
 * ルート守衛が参照する。LINE コールバックは未ログインのログイン/登録経路が通るため公開扱いが必須で、
 * 連携経路（ログイン済み）でも会員・キャストのコンソールから /admin/* へ到達するため
 * コンソール整合ガードの対象外である必要がある。
 */
export function isPublicAuthPath(path: string): boolean {
  return (
    path.startsWith('/auth/login') ||
    path.startsWith('/auth/invite') ||
    path.startsWith('/auth/register') ||
    path.startsWith('/auth/line/callback')
  );
}

/**
 * セッション種別に依らず到達させる共有 admin ルート（自アカウント設定）。
 * 種別/エリア整合ガードの対象外にする。
 */
export function isSharedAdminPath(path: string): boolean {
  return path === '/admin/settings/account' || path.startsWith('/admin/settings/account/');
}

/** 認証済みコンソールの URL エリア。公開ストアフロント（/casts 等）は cast に含めない。 */
export function consoleAreaOfPath(path: string): 'admin' | 'store' | 'cast' | 'member' | null {
  if (path.startsWith('/admin')) return 'admin';
  if (path.startsWith('/store')) return 'store';
  if (path === '/cast' || path.startsWith('/cast/')) return 'cast';
  if (path === '/member' || path.startsWith('/member/')) return 'member';
  return null;
}

/**
 * 旧前綴 /platform/** のブックマーク互換。認証系（login / register / invite / line/callback）は
 * /auth、それ以外は /admin に対応する。旧前綴でなければ undefined。
 */
export function legacyPlatformPathTarget(path: string): string | undefined {
  const match = /^\/platform(\/.*)?$/.exec(path);
  if (!match) return undefined;
  const rest = match[1] ?? '';
  const isAuth = /^\/(?:login|register|invite|line\/callback)(?:\/|$)/.test(rest);
  return `${isAuth ? '/auth' : '/admin'}${rest}`;
}
