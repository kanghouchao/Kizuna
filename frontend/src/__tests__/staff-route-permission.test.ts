import { readdirSync, readFileSync, statSync } from 'fs';
import { join, relative, sep } from 'path';

/**
 * 従業員の管理画面の各画面は、権限が無ければ着地へ戻す門をどこかに持つ。route の殻で PermissionGate に
 * 包むか、ページ自身が権限を判定して DeniedPage を描くかのどちらか。門の無い画面はバックエンドの 403 を
 * 領域内の失敗として出したまま居座るので、新しい画面はここのどちらかの一覧に載るまで落ちる。
 */
const STAFF_GROUP = join(__dirname, '..', 'app', '(admin)', '(staff)');

/** 権限を要さない画面（着地・ダッシュボード・本人のアカウント設定）。 */
const OPEN = [
  'admin',
  'admin/dashboard',
  'admin/dashboard-prototype',
  'admin/settings/account',
  'store/[storeId]/dashboard',
  'store/[storeId]/dashboard-prototype',
];

/** ページ自身が権限を判定し、拒めば DeniedPage を描く画面。ページ側の判定はここでは確かめず、各ページのテストが持つ。 */
const GATED_IN_PAGE = [
  'admin/notification-deliveries',
  'admin/operational-reports',
  'admin/orders',
  'store/[storeId]/advertising-costs',
  'store/[storeId]/advertising-costs/media',
  'store/[storeId]/articles',
  'store/[storeId]/articles/create',
  'store/[storeId]/articles/[id]/edit',
  'store/[storeId]/customers/duplicates',
  'store/[storeId]/customers/merge',
  'store/[storeId]/notification-deliveries',
  'store/[storeId]/operational-reports',
  'store/[storeId]/orders/monthly-remunerations',
  'store/[storeId]/orders/[id]/completion-invalidation',
  'store/[storeId]/orders/[id]/correction',
  'store/[storeId]/orders/[id]/point-rollback',
  'store/[storeId]/reviews',
  'store/[storeId]/reviews/create',
  'store/[storeId]/reviews/[id]',
  'store/[storeId]/reviews/[id]/edit',
  'store/[storeId]/services',
  'store/[storeId]/surveys',
  'store/[storeId]/surveys/create',
  'store/[storeId]/surveys/[sid]/revisions/new',
  'store/[storeId]/surveys/[sid]/revisions/[rid]',
  'store/[storeId]/surveys/[sid]/revisions/[rid]/edit',
  'store/[storeId]/surveys/[sid]/revisions/[rid]/responses/new',
  'store/[storeId]/surveys/responses/[aid]',
  'store/[storeId]/surveys/responses/[aid]/correct',
];

function routes(dir: string): string[] {
  return readdirSync(dir).flatMap(entry => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) return routes(full);
    return entry === 'page.tsx' ? [relative(STAFF_GROUP, dir).split(sep).join('/')] : [];
  });
}

describe('従業員の管理画面の権限の門', () => {
  const all = routes(STAFF_GROUP);

  it.each(all.filter(route => !OPEN.includes(route) && !GATED_IN_PAGE.includes(route)))(
    '%s は route の殻で PermissionGate に包む',
    route => {
      const shell = readFileSync(join(STAFF_GROUP, route, 'page.tsx'), 'utf8');
      expect(shell).toMatch(/<PermissionGate codes=\{\[[^\]]+\]\}>/);
    }
  );

  it('一覧に載る画面は実在し、殻で包む画面と重ならない', () => {
    for (const route of [...OPEN, ...GATED_IN_PAGE]) {
      expect(all).toContain(route);
      expect(readFileSync(join(STAFF_GROUP, route, 'page.tsx'), 'utf8')).not.toContain(
        'PermissionGate'
      );
    }
  });
});
