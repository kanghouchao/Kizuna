import { type MenuTree, useMenuTrees } from '@/entities/menu';
import { type AuthorizedStore, useStoreContext } from '@/entities/user';
import { storePath } from '@/shared/lib';

const GROUP_DASHBOARD = '/admin/dashboard';

/**
 * 何も選んでいない着地は一階層目（レール）の先頭: グループの木があればグループのダッシュボード、無ければ
 * 作成日の新しい店舗（/stores/me の先頭）のダッシュボード。どちらも無い者は全員の着地先へ置く。
 */
export function landingPath(trees: MenuTree[], stores: AuthorizedStore[]): string {
  if (trees.some(tree => tree.tree === 'GROUP')) return GROUP_DASHBOARD;
  const first = stores[0]?.id;
  if (trees.some(tree => tree.tree === 'STORE') && first !== undefined) {
    return storePath(first, '/dashboard');
  }
  return GROUP_DASHBOARD;
}

/** 本人の着地先。メニューの木と授権店舗が揃うまでは undefined（授権店舗の取得失敗は店舗なしとして扱う）。 */
export function useLandingPath(): string | undefined {
  const trees = useMenuTrees();
  const { stores, loadFailed } = useStoreContext();
  if (trees === null || (stores === null && !loadFailed)) return undefined;
  return landingPath(trees, stores ?? []);
}
