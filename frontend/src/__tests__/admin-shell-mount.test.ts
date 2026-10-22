import { existsSync, readdirSync, readFileSync, statSync } from 'fs';
import { join, relative } from 'path';

/**
 * 管理画面のシェルは /admin と /store の共通祖先の layout にだけ載る。どちらかの配下で再び載せると、
 * 跨ぐ遷移の度にシェルが作り直され、授権店舗一覧と本人情報を取り直す（実遷移は e2e の
 * hybrid-console-access が要求回数で固定する）。
 */
const APP_ROOT = join(__dirname, '..', 'app');
const STAFF_GROUP = join(APP_ROOT, '(admin)', '(staff)');

function collect(dir: string): string[] {
  return readdirSync(dir).flatMap(entry => {
    const full = join(dir, entry);
    return statSync(full).isDirectory() ? collect(full) : /\.(ts|tsx)$/.test(entry) ? [full] : [];
  });
}

describe('管理画面シェルの搭載位置', () => {
  it('/admin と /store は同じ route group の子である', () => {
    expect(existsSync(join(STAFF_GROUP, 'admin'))).toBe(true);
    expect(existsSync(join(STAFF_GROUP, 'store'))).toBe(true);
  });

  it('AdminShell を描くのは route group の layout だけである', () => {
    const mounts = collect(APP_ROOT)
      .filter(file => readFileSync(file, 'utf8').includes('@/widgets/admin-shell'))
      .map(file => relative(APP_ROOT, file));
    expect(mounts).toEqual([join('(admin)', '(staff)', 'layout.tsx')]);
  });
});
