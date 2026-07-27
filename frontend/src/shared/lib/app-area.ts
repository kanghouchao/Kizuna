/**
 * パスがどの領域に属するかの唯一の判定。ルート守衛とテーマの作用範囲が同じ知識を参照する
 * ための module で、両者が別々の前綴リストを持つと片方だけが取り残される。
 */

/** セッション不要の platform ルート（ログインフォーム・招待受諾）。 */
export function isPublicPlatformPath(path: string): boolean {
  return path.startsWith('/platform/login') || path.startsWith('/platform/invite');
}

/**
 * admin のトークン層で描かれる領域か。公開店舗サイトと、独自の CSS を持つ認証画面は含まない。
 * ここに含まれる画面だけがテーマ（ライト / ダーク）の対象になる。
 */
export function isTokenThemedPath(path: string | null | undefined): boolean {
  if (!path) return false;
  if (isPublicPlatformPath(path)) return false;
  return (
    path.startsWith('/platform') ||
    path.startsWith('/store') ||
    path === '/cast' ||
    path.startsWith('/cast/')
  );
}
