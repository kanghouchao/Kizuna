import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';

/**
 * 店舗パス組立と店舗コンソール資格判定の集約を守る負向不変量。
 * frontend/src 全体を fs 走査し、集約後に消滅しているべき字面が復活していないことを機械判定する。
 */
const SRC_ROOT = join(__dirname, '..');
// 店舗パス組立の唯一の許可元。ここ以外に店舗パステンプレート字面が現れてはならない。
const STORE_ROUTE_MODULE = join(SRC_ROOT, 'shared', 'lib', 'store-route.ts');
// 本テスト自身は走査対象外（トークンを含むため）。
const SELF = __filename;

function collectSourceFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...collectSourceFiles(full));
    } else if (/\.(ts|tsx)$/.test(entry) && full !== SELF) {
      out.push(full);
    }
  }
  return out;
}

const files = collectSourceFiles(SRC_ROOT);
const contentOf = (path: string) => readFileSync(path, 'utf8');

describe('店舗コンテキスト集約の負向不変量', () => {
  it('STORE_CONSOLE_CAPABILITIES 字面リストが frontend/src から消滅している（手写し鏡像の撤去）', () => {
    const offenders = files.filter(f => contentOf(f).includes('STORE_CONSOLE_CAPABILITIES'));
    expect(offenders).toEqual([]);
  });

  it('裸の店舗パステンプレート字面が store-route.ts 以外に存在しない（店舗パス組立は store-route 経由のみ）', () => {
    // 自己マッチを避けるため検出トークンを分割して構築する。
    const token = '/store/' + '${';
    const offenders = files
      .filter(f => f !== STORE_ROUTE_MODULE)
      .filter(f => contentOf(f).includes(token));
    expect(offenders).toEqual([]);
  });
});
