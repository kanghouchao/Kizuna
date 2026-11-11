/**
 * 店舗の文脈のパスを組み・読む唯一の入口。店舗の文脈は URL の店舗 id が決め（`/store/{storeId}/...`）、
 * 画面の店舗・X-Store-ID・レールの選択はすべて storeIdOf の読みに従う。
 */

/** subPath は '/' 始まりの店舗の中のパス（例 '/casts/create'。メニューの店舗の木の path もこの形）。 */
export function storePath(storeId: number | string, subPath: string): string {
  return `/store/${storeId}${subPath}`;
}

/** 店舗の文脈の画面なら URL の店舗 id を返す。数字でない id は店舗を名指さない。 */
export function storeIdOf(pathname: string): string | undefined {
  return /^\/store\/(\d+)(?:\/|$)/.exec(pathname)?.[1];
}

/** 店舗級の画面か。/store そのものと /store/ 配下だけが該当し、/storefront のような前方一致は含めない。 */
export function isStorePath(pathname: string): boolean {
  return pathname === '/store' || pathname.startsWith('/store/');
}
