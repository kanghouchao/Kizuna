import { renderToStaticMarkup } from 'react-dom/server';
import RootLayout from '../app/layout';
import StoreLayout from '../app/store/layout';

// テーマ配線は管理コンソールだけの関心事。店舗ドメインは公開サイトとコンソールを同一 origin で
// 配信するため、provider が根 layout に載ると <html> の class と color-scheme が公開サイトにも
// 及ぶ。そこで「根には無い」と「コンソールには規定の props で有る」を対で錨にする。
const themeProviderProps: Record<string, unknown>[] = [];

jest.mock('@/shared/ui', () => ({
  ThemeProvider: (props: { children: React.ReactNode }) => {
    themeProviderProps.push(props);
    return props.children;
  },
}));

jest.mock('@/entities/user', () => ({
  AuthProvider: (props: { children: React.ReactNode }) => props.children,
  StoreContextProvider: (props: { children: React.ReactNode }) => props.children,
}));

jest.mock('@/_app/providers', () => ({
  ToastProvider: () => null,
}));

jest.mock('@/widgets/sidebar', () => ({ Sidebar: () => null }));
jest.mock('@/widgets/header', () => ({ Header: () => null }));

describe('テーマ provider の作用範囲', () => {
  beforeEach(() => {
    themeProviderProps.length = 0;
  });

  it('根 layout はテーマを配線しない（公開店舗サイトへ及ばせない）', () => {
    renderToStaticMarkup(<RootLayout>本文</RootLayout>);

    expect(themeProviderProps).toHaveLength(0);
  });

  it('店舗コンソールはテーマを配線し、強制も保存先の差し替えもしない', () => {
    renderToStaticMarkup(<StoreLayout>本文</StoreLayout>);

    expect(themeProviderProps).toHaveLength(1);
    expect(themeProviderProps[0]).toMatchObject({
      attribute: 'class',
      defaultTheme: 'system',
      enableSystem: true,
    });
    expect(themeProviderProps[0]).not.toHaveProperty('forcedTheme');
    expect(themeProviderProps[0]).not.toHaveProperty('storageKey');
  });
});
