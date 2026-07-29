const STORE_ID_PATTERN = /^\/store\/(\d+)(?:\/|$)/;
// /store/entry（およびその配下）は storeId を含まない静的ルート。
const STORE_ENTRY_PATTERN = /^\/store\/entry(?:\/|$)/;
// storeId 移設（/store/... → /store/[storeId]/...）以前の id 無し店舗パス（例 /store/orders）。
// /store/entry 自体と数値id配下（/store/5/...）は対象外 — entry を除外しないと入口ルート自身が
// レガシー判定に掛かり、守衛が entry へ差し戻す先も entry になって無限リダイレクトになる。
const LEGACY_STORE_PATH_PATTERN = /^\/store\/(?!entry\b)(?!\d+(\/|$))/;

export function getStoreIdFromPath(pathname: string): string | undefined {
  return STORE_ID_PATTERN.exec(pathname)?.[1];
}

export function replaceStoreIdInPath(pathname: string, newStoreId: number | string): string {
  if (STORE_ID_PATTERN.test(pathname)) {
    return pathname.replace(STORE_ID_PATTERN, `/store/${newStoreId}/`);
  }
  // /store/entry は sub-path 保存だと実在しない /store/{id}/entry を生むため除外する。
  if (pathname.startsWith('/store') && !STORE_ENTRY_PATTERN.test(pathname)) {
    return `/store/${newStoreId}${pathname.slice(6)}`;
  }
  // 店舗スコープ外（/platform 配下など）からの切替。着地先はメニュー由来で入口ルートが解決する。
  return storeEntryPath();
}

/** 店舗ルートを組む唯一の入口。subPath は '/' 始まり（例 '/casts/create'）。 */
export function storePath(storeId: string, subPath: string): string {
  return `/store/${storeId}${subPath}`;
}

/** 店舗コンソールの入口ルート。next（解決後の遷移先テンプレート）を渡すと encode して付与する。 */
export function storeEntryPath(next?: string): string {
  return next ? `/store/entry?next=${encodeURIComponent(next)}` : '/store/entry';
}

/**
 * 店舗スコープの menu path（例 /store/orders）に storeId を埋め込む（Sidebar 由来）。
 * /store 以外のパスは無加工で通し、storeId 確定時は /store の直後へ挿入する。
 * storeId 未確定時は入口ルート（next 保存）へ誘導する。認可の根拠ではなく遷移先の解決のみ
 * — 非授権店舗はバックエンドが fail-closed で拒否する。
 */
export function resolveStoreHref(itemPath: string, storeId: string | undefined): string {
  if (!itemPath.startsWith('/store')) {
    return itemPath;
  }
  if (storeId) {
    return itemPath.replace('/store', `/store/${storeId}`);
  }
  return storeEntryPath(itemPath);
}

/** storeId 移設以前の id 無し店舗パス（レガシーブックマーク）か。routeGuard が誘導判定に使う。 */
export function isLegacyStorePath(pathname: string): boolean {
  return LEGACY_STORE_PATH_PATTERN.test(pathname);
}
