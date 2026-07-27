import { renderToStaticMarkup } from 'react-dom/server';
import RootLayout from '../app/layout';

// 根 layout はテーマ配線の唯一の設置点。ここに forcedTheme が戻る／既定が system から
// 外れる／storageKey が挿し変わると、モード切替は UI 側を一切壊さないまま無効化される。
// そのため provider へ渡る props そのものを錨にする。
const themeProviderProps: Record<string, unknown>[] = [];

jest.mock('@/shared/ui', () => ({
  ThemeProvider: (props: { children: React.ReactNode }) => {
    themeProviderProps.push(props);
    return props.children;
  },
}));

jest.mock('@/entities/user', () => ({
  AuthProvider: (props: { children: React.ReactNode }) => props.children,
}));

jest.mock('@/_app/providers', () => ({
  ToastProvider: () => null,
}));

describe('根 layout のテーマ配線', () => {
  beforeEach(() => {
    themeProviderProps.length = 0;
    renderToStaticMarkup(<RootLayout>本文</RootLayout>);
  });

  it('テーマは強制されない（forcedTheme を渡さない）', () => {
    expect(themeProviderProps).toHaveLength(1);
    expect(themeProviderProps[0]).not.toHaveProperty('forcedTheme');
  });

  it('既定は system で、OS 追随が有効になっている', () => {
    expect(themeProviderProps[0]).toMatchObject({
      attribute: 'class',
      defaultTheme: 'system',
      enableSystem: true,
    });
  });

  it('保存先の差し替えはしない（既定の storageKey のまま永続する）', () => {
    expect(themeProviderProps[0]).not.toHaveProperty('storageKey');
  });
});
