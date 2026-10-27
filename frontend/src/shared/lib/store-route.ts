/** 店舗ルートを組む唯一の入口。subPath は '/' 始まり（例 '/casts/create'）。現在店舗は URL に載せない。 */
export function storePath(subPath: string): string {
  return `/store${subPath}`;
}
