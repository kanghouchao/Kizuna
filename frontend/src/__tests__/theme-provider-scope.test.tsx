import { render } from '@testing-library/react';
import fs from 'fs';
import path from 'path';
import { ThemeScope } from '@/_app/providers';

// テーマ配線は管理コンソールだけの関心事。作用範囲は根 layout の route group 分割で
// 構造的に隔離される（(admin) だけが ThemeScope を持ち、(public) はテーマ配線を一切
// 持たない）。ここではその構造と、(admin) 側 provider の props 錨を守る。
const themeProviderProps: Record<string, unknown>[] = [];

jest.mock('@/shared/ui', () => ({
  ThemeProvider: (props: { children: React.ReactNode }) => {
    themeProviderProps.push(props);
    return props.children;
  },
}));

const appDir = path.join(__dirname, '..', 'app');

function sourceFilesUnder(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return sourceFilesUnder(full);
    return /\.(ts|tsx)$/.test(entry.name) ? [full] : [];
  });
}

describe('テーマ provider の作用範囲', () => {
  it('ThemeScope は既定 system・OS 追随で、現在地による強制や保存先の差し替えはしない', () => {
    themeProviderProps.length = 0;
    render(<ThemeScope>本文</ThemeScope>);

    expect(themeProviderProps).toHaveLength(1);
    const props = themeProviderProps[0];
    expect(props).toMatchObject({
      attribute: 'class',
      defaultTheme: 'system',
      enableSystem: true,
    });
    expect(props).not.toHaveProperty('forcedTheme');
    expect(props).not.toHaveProperty('storageKey');
  });

  it('(public) の route 殻にはテーマ配線が存在しない', () => {
    const files = sourceFilesUnder(path.join(appDir, '(public)'));
    expect(files.length).toBeGreaterThan(0);
    for (const file of files) {
      const source = fs.readFileSync(file, 'utf8');
      expect(source).not.toMatch(/next-themes|ThemeProvider|ThemeScope/);
    }
  });

  it('(admin) の根 layout だけがテーマを配線する', () => {
    const adminLayout = fs.readFileSync(path.join(appDir, '(admin)', 'layout.tsx'), 'utf8');
    // next-themes が <html> の class を書き換えるため、hydration 差分の抑止が必要
    expect(adminLayout).toContain('suppressHydrationWarning');
    expect(adminLayout).toContain('ThemeScope');

    const wired = sourceFilesUnder(appDir).filter(file =>
      fs.readFileSync(file, 'utf8').includes('ThemeScope')
    );
    expect(wired).toEqual([path.join(appDir, '(admin)', 'layout.tsx')]);
  });
});
