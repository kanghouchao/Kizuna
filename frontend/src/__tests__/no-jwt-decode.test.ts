import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';

/**
 * フロントは JWT の payload を解読しない（本人の種別・権限・メールアドレスは GET /me から読む）。
 * payload の取り出しに要る base64 の復号口が製品コードに現れないことを機械判定する。
 */
const SRC_ROOT = join(__dirname, '..');

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

describe('JWT を解読しない不変量', () => {
  it('製品コードに base64 の復号（atob / jwt-decode / Buffer の base64 読み）が無い', () => {
    const offenders = collectProductFiles(SRC_ROOT).filter(f =>
      /\batob\(|jwt-decode|jwtDecode|['"]base64(url)?['"]/.test(readFileSync(f, 'utf8'))
    );
    expect(offenders).toEqual([]);
  });
});
