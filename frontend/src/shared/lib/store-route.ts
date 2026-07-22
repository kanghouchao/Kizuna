const STORE_ID_PATTERN = /^\/store\/(\d+)(?:\/|$)/;
// /store/select（およびその配下）は storeId を含まない静的ルート。
const STORE_SELECT_PATTERN = /^\/store\/select(?:\/|$)/;
// storeId 移設（/store/... → /store/[storeId]/...）以前の id 無し店舗パス（例 /store/orders）。
// /store/select 自体と数値id配下（/store/5/...）は対象外（#413 Fix6-2 の正規表現をここへ集約）。
const LEGACY_STORE_PATH_PATTERN = /^\/store\/(?!select\b)(?!\d+(\/|$))/;

export function getStoreIdFromPath(pathname: string): string | undefined {
  return STORE_ID_PATTERN.exec(pathname)?.[1];
}

export function replaceStoreIdInPath(pathname: string, newStoreId: number | string): string {
  if (STORE_ID_PATTERN.test(pathname)) {
    return pathname.replace(STORE_ID_PATTERN, `/store/${newStoreId}/`);
  }
  // /store/select は sub-path 保存だと実在しない /store/{id}/select を生むため除外し、
  // dashboard へフォールバックする（#413 Fix5-2）。
  if (pathname.startsWith('/store') && !STORE_SELECT_PATTERN.test(pathname)) {
    return `/store/${newStoreId}${pathname.slice(6)}`;
  }
  return `/store/${newStoreId}/dashboard`;
}

/** 店舗ルートを組む唯一の入口。subPath は '/' 始まり（例 '/casts/create'）。 */
export function storePath(storeId: string, subPath: string): string {
  return `/store/${storeId}${subPath}`;
}

/** 店舗選択ルート。next（選択後の遷移先テンプレート）を渡すと encode して付与する。 */
export function storeSelectPath(next?: string): string {
  return next ? `/store/select?next=${encodeURIComponent(next)}` : '/store/select';
}

/**
 * 店舗スコープの menu path（例 /store/orders）に storeId を埋め込む（Sidebar 由来 — #413）。
 * /store 以外のパスは無加工で通し、storeId 確定時は /store の直後へ挿入する。
 * storeId 未確定時は店舗選択ルート（next 保存）へ誘導する。認可の根拠ではなく遷移先の解決のみ
 * — 非授権店舗はバックエンドが fail-closed で拒否する。
 */
export function resolveStoreHref(itemPath: string, storeId: string | undefined): string {
  if (!itemPath.startsWith('/store')) {
    return itemPath;
  }
  if (storeId) {
    return itemPath.replace('/store', `/store/${storeId}`);
  }
  return storeSelectPath(itemPath);
}

/** storeId 移設以前の id 無し店舗パス（レガシーブックマーク）か。routeGuard が誘導判定に使う。 */
export function isLegacyStorePath(pathname: string): boolean {
  return LEGACY_STORE_PATH_PATTERN.test(pathname);
}
