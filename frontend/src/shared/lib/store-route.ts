// 店舗 id を URL に持っていた頃の店舗パス（例 /store/5/orders）。ブックマーク・共有リンクに残りうる。
const LEGACY_STORE_PATH_PATTERN = /^\/store\/(\d+)(\/.*)?$/;

/** 店舗ルートを組む唯一の入口。subPath は '/' 始まり（例 '/casts/create'）。現在店舗は URL に載せない。 */
export function storePath(subPath: string): string {
  return `/store${subPath}`;
}

/** 店舗 id を URL に持つ旧形式の店舗パスか。 */
export function isLegacyStorePath(pathname: string): boolean {
  return LEGACY_STORE_PATH_PATTERN.test(pathname);
}

/**
 * 旧形式の店舗パスを店舗 id と id 以降の subPath（'/' 始まり、id 直後で終わる場合は空文字）へ分ける。
 * 旧形式でなければ undefined。routeGuard が id 無しの形へ誘導するときに使う。
 */
export function splitLegacyStorePath(
  pathname: string
): { storeId: string; subPath: string } | undefined {
  const match = LEGACY_STORE_PATH_PATTERN.exec(pathname);
  if (!match) return undefined;
  return { storeId: match[1], subPath: match[2] ?? '' };
}
