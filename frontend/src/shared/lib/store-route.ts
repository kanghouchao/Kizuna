const STORE_ID_PATTERN = /^\/store\/(\d+)(?:\/|$)/;
// /store/select（およびその配下）は storeId を含まない静的ルート。
const STORE_SELECT_PATTERN = /^\/store\/select(?:\/|$)/;

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
