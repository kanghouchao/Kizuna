/** 店舗ルートを組む唯一の入口。subPath は '/' 始まり（例 '/casts/create'）。現在店舗は URL に載せない。 */
export function storePath(subPath: string): string {
  return `/store${subPath}`;
}

/** 店舗級の画面か。/store そのものと /store/ 配下だけが該当し、/storefront のような前方一致は含めない。 */
export function isStorePath(pathname: string): boolean {
  return pathname === '/store' || pathname.startsWith('/store/');
}
