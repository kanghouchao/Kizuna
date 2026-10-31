import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';

/**
 * フロントは JWT の payload を解読しない（本人の種別・権限・メールアドレスは GET /me から読む）。
 * payload の取り出しに要る base64 の復号口が製品コードに現れないことを機械判定する。
 * あわせて、アクセストークンをタブのメモリの外（cookie・localStorage・sessionStorage）へ書かないことを判定する。
 */
const SRC_ROOT = join(__dirname, '..');
const relative = (file: string) => file.slice(SRC_ROOT.length + 1);

function collectProductFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (entry !== '__tests__') out.push(...collectProductFiles(full));
    } else if (/\.(ts|tsx)$/.test(entry) && !/\.test\.tsx?$/.test(entry)) {
      out.push(full);
    }
  }
  return out;
}

describe('JWT を解読せず、トークンをメモリの外へ書かない不変量', () => {
  it('製品コードに base64 の復号（atob / jwt-decode / Buffer の base64 読み）が無い', () => {
    const offenders = collectProductFiles(SRC_ROOT).filter(f =>
      /\batob\(|jwt-decode|jwtDecode|['"]base64(url)?['"]/.test(readFileSync(f, 'utf8'))
    );
    expect(offenders).toEqual([]);
  });

  it('製品コードに token という名前の cookie の読み書きが無い', () => {
    const offenders = collectProductFiles(SRC_ROOT).filter(f =>
      /\.(get|set|remove|delete)\(\s*['"]token['"]/.test(readFileSync(f, 'utf8'))
    );
    expect(offenders).toEqual([]);
  });

  it('アクセストークンを取り出せるのは置き場と API クライアントだけで、どちらも持続する保存領域に触れない', () => {
    // 値の流れは静的に追えないので、トークンに届く口を 2 ファイルに閉じ、その 2 ファイルが
    // cookie・Web Storage を使わないことで「メモリの外へ書かない」を担保する。
    const holders = ['shared/api/client.ts', 'shared/api/session.ts'];
    const readers = collectProductFiles(SRC_ROOT)
      .filter(f => /\bgetAccessToken\b/.test(readFileSync(f, 'utf8')))
      .map(relative)
      .sort();
    expect(readers).toEqual(holders);
    for (const holder of holders) {
      expect(readFileSync(join(SRC_ROOT, holder), 'utf8')).not.toMatch(
        /['"]js-cookie['"]|document\.cookie|\b(localStorage|sessionStorage|indexedDB)\./
      );
    }
  });
});
