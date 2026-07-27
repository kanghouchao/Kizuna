import { render } from '@testing-library/react';
import { usePathname } from 'next/navigation';
import { ThemeScope } from '@/_app/providers';

// テーマ配線は管理コンソールだけの関心事。店舗ドメインは公開サイトとコンソールを同一 origin で
// 配信するため、コンソール外では light を強制して <html> の class と color-scheme を現在地へ
// 追随させる。next-themes はアンマウント時に適用済みの class を取り除かないので、provider の
// 設置場所ではなく forcedTheme が唯一の防壁になる。
const themeProviderProps: Record<string, unknown>[] = [];

jest.mock('@/shared/ui', () => ({
  ThemeProvider: (props: { children: React.ReactNode }) => {
    themeProviderProps.push(props);
    return props.children;
  },
}));

jest.mock('next/navigation', () => ({
  usePathname: jest.fn(),
}));

const mockedUsePathname = usePathname as jest.MockedFunction<typeof usePathname>;

function propsFor(pathname: string) {
  themeProviderProps.length = 0;
  mockedUsePathname.mockReturnValue(pathname);
  render(<ThemeScope>本文</ThemeScope>);
  expect(themeProviderProps).toHaveLength(1);
  return themeProviderProps[0];
}

describe('テーマ provider の作用範囲', () => {
  it.each([
    '/platform/staff',
    '/platform/settings/account',
    '/store/1/customers',
    '/store/select',
    '/cast',
    '/cast/requests',
  ])('トークン層で描かれる %s ではテーマを強制しない', path => {
    expect(propsFor(path)).not.toHaveProperty('forcedTheme', 'light');
  });

  it.each([
    '/',
    '/casts',
    '/casts/12',
    '/schedule',
    '/menu',
    '/about',
    '/platform/login',
    '/platform/invite/abc123',
  ])('トークン層の外にある %s では light を強制する', path => {
    expect(propsFor(path)).toMatchObject({ forcedTheme: 'light' });
  });

  it('トークン層の画面では既定 system・OS 追随で、保存先の差し替えはしない', () => {
    const props = propsFor('/store/1/customers');

    expect(props).toMatchObject({
      attribute: 'class',
      defaultTheme: 'system',
      enableSystem: true,
    });
    expect(props).not.toHaveProperty('storageKey');
  });
});
