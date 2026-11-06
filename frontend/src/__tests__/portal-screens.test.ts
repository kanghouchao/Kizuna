import { readdirSync, statSync } from 'fs';
import { join, relative, sep } from 'path';
import { CAST_SCREENS } from '@/_pages/cast-portal/ui/CastPortalLayout';
import { MEMBER_SCREENS } from '@/_pages/member-portal/ui/MemberPortalLayout';

/**
 * 本人ポータルの外枠は画面名・戻り先・所属タブを表で引く。表に無いルートは題名帯が空になり
 * 戻る導線も出ないため、ルートを足したら表にも足すことをここで強制する。
 */
const APP_ROOT = join(__dirname, '..', 'app', '(admin)');

function routes(portal: string): string[] {
  const walk = (dir: string): string[] =>
    readdirSync(dir).flatMap(entry => {
      const full = join(dir, entry);
      if (statSync(full).isDirectory()) return walk(full);
      return entry === 'page.tsx' ? [full] : [];
    });
  return walk(join(APP_ROOT, portal))
    .map(
      file =>
        '/' +
        relative(APP_ROOT, file)
          .split(sep)
          .slice(0, -1)
          // ルートグループ「(name)」は URL に現れない
          .filter(segment => !/^\(.+\)$/.test(segment))
          .join('/')
    )
    .sort();
}

describe('本人ポータルの画面の表', () => {
  it.each([
    ['cast', CAST_SCREENS],
    ['member', MEMBER_SCREENS],
  ])('%s のすべてのルートが表に載り、表に実在しないルートが無い', (portal, screens) => {
    expect(Object.keys(screens).sort()).toEqual(routes(portal));
  });

  it.each([
    ['cast', CAST_SCREENS],
    ['member', MEMBER_SCREENS],
  ])('%s の戻り先と所属タブは表に載った画面である', (_portal, screens) => {
    for (const screen of Object.values(screens)) {
      const target = 'back' in screen ? screen.back : screen.tab;
      expect(screens[target]).toBeDefined();
    }
  });
});
