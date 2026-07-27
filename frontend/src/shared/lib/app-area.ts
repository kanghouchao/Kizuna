/** セッション不要の platform ルート（ログインフォーム・招待受諾）。ルート守衛が参照する。 */
export function isPublicPlatformPath(path: string): boolean {
  return path.startsWith('/platform/login') || path.startsWith('/platform/invite');
}
