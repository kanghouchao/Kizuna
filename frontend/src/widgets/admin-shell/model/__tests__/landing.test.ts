import type { MenuTree } from '@/entities/menu';
import { landingPath } from '../landing';

const group: MenuTree = { tree: 'GROUP', items: [] };
const store: MenuTree = { tree: 'STORE', items: [] };
// /stores/me は作成日の新しい順で返す。先頭が作成日の最も新しい店舗。
const stores = [{ id: 7 }, { id: 3 }];

describe('landingPath（一階層目の先頭への着地）', () => {
  it.each([
    ['グループの木があればグループのダッシュボード', [group, store], stores, '/admin/dashboard'],
    ['店舗の木だけなら作成日の新しい店舗のダッシュボード', [store], stores, '/store/7/dashboard'],
    ['店舗の木だけで授権店舗が無ければグループのダッシュボード', [store], [], '/admin/dashboard'],
    ['どの木も無ければグループのダッシュボード', [], stores, '/admin/dashboard'],
  ])('%s', (_, trees, list, expected) => {
    expect(landingPath(trees, list)).toBe(expected);
  });
});
