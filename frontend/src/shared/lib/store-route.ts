const STORE_ID_PATTERN = /^\/store\/(\d+)(?:\/|$)/;

export function getStoreIdFromPath(pathname: string): string | undefined {
  return STORE_ID_PATTERN.exec(pathname)?.[1];
}

export function replaceStoreIdInPath(pathname: string, newStoreId: number | string): string {
  return STORE_ID_PATTERN.test(pathname)
    ? pathname.replace(STORE_ID_PATTERN, `/store/${newStoreId}/`)
    : `/store/${newStoreId}${pathname.startsWith('/store') ? pathname.slice(6) : '/dashboard'}`;
}
