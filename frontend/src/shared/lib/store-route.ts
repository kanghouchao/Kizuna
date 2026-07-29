const STORE_ID_PATTERN = /^\/store\/(\d+)(?:\/|$)/;
// /store/entry（およびその配下・クエリ付き）は storeId を含まない静的ルート。
const STORE_ENTRY_PATTERN = /^\/store\/entry(?:[/?]|$)/;
// storeId 移設（/store/... → /store/[storeId]/...）以前の id 無し店舗パス（例 /store/orders）。
// /store/entry 自体と数値id配下（/store/5/...）は対象外 — entry を除外しないと入口ルート自身が
// レガシー判定に掛かり、守衛が entry へ差し戻す先も entry になって無限リダイレクトになる。
const LEGACY_STORE_PATH_PATTERN = /^\/store\/(?!entry\b)(?!\d+(\/|$))/;
// 廃止済みの店舗ルート。id 無し（/store/dashboard）は id 付きへ解決すると実在しない画面を指し、
// id 付き（/store/5/dashboard・移設後の正規 URL だったためブックマークに残る）はレガシー判定が
// 数値 id 配下を除外するので素の 404 になる。どちらも入口へ収容し、遷移先は捨てる。
const RETIRED_STORE_PATH_PATTERN = /^\/store\/(?:\d+\/)?(select|dashboard)(?:\/|$)/;

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
  // 入口ルートは storeId を持たない静的ルート。埋めると実在しない /store/{id}/entry になる。
  // ここを抜くと、メニュー障害時にサイドバーが出す唯一の店舗コンソール導線が 404 になる。
  if (STORE_ENTRY_PATTERN.test(itemPath)) {
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

/** 入口ルート自身（およびその配下）か。入口が自分自身を遷移先に取らないための判定。 */
export function isStoreEntryPath(pathname: string): boolean {
  return STORE_ENTRY_PATTERN.test(pathname);
}

/** 遷移先として復元してはいけない廃止済みルートか。入口が next を捨てる判定に使う。 */
export function isRetiredStorePath(pathname: string): boolean {
  return RETIRED_STORE_PATH_PATTERN.test(pathname);
}
