/** セッション不要の platform ルート（ログインフォーム・招待受諾）。ルート守衛が参照する。 */
export function isPublicPlatformPath(path: string): boolean {
  return path.startsWith('/platform/login') || path.startsWith('/platform/invite');
}

/**
 * コンソール種別に依らず到達させる共有 platform ルート（自アカウント設定）。
 * 平台ドメイン上の店舗コンソールは Header からここへリンクするため、
 * コンソール/エリア整合ガードの対象外にする。
 */
export function isSharedPlatformPath(path: string): boolean {
  return path === '/platform/settings/account' || path.startsWith('/platform/settings/account/');
}

/** 認証済みコンソールの URL エリア。公開ストアフロント（/casts 等）は cast に含めない。 */
export function consoleAreaOfPath(path: string): 'platform' | 'store' | 'cast' | null {
  if (path.startsWith('/platform')) return 'platform';
  if (path.startsWith('/store')) return 'store';
  if (path === '/cast' || path.startsWith('/cast/')) return 'cast';
  return null;
}
