import { render, screen, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { Sidebar } from '../Sidebar';
import { menuApi } from '@/entities/menu';

jest.mock('js-cookie');

let mockPathname = '/store/dashboard';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/entities/menu', () => ({
  menuApi: { getMenus: jest.fn() },
}));

const mockedGetMenus = menuApi.getMenus as jest.Mock;

// 店舗スコープ項目と平台項目を1つずつ持つメニュー（href 解決の検証用）。
const menuWithStoreAndPlatform = [
  {
    name: 'メイン',
    items: [
      { name: '受注一覧', path: '/store/orders', icon: 'HomeIcon' },
      { name: '店舗一覧', path: '/platform/stores', icon: 'HomeIcon' },
    ],
  },
];

describe('Sidebar', () => {
  beforeEach(() => {
    mockPathname = '/store/dashboard';
    mockedGetMenus.mockResolvedValue([]);
    (Cookies.get as jest.Mock).mockImplementation(() => undefined);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('store コンソール cookie でも同一 menuApi が呼ばれる', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'store' : undefined
    );

    render(<Sidebar />);

    await waitFor(() => expect(menuApi.getMenus).toHaveBeenCalled());
    expect(menuApi.getMenus).toHaveBeenCalledTimes(1);
  });

  it('platform コンソール cookie でも同一 menuApi が呼ばれる', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'platform' : undefined
    );

    render(<Sidebar />);

    await waitFor(() => expect(menuApi.getMenus).toHaveBeenCalled());
    expect(menuApi.getMenus).toHaveBeenCalledTimes(1);
  });

  it('path 由来 storeId を店舗リンクに埋め込み、平台リンクは無加工にする', async () => {
    mockPathname = '/store/2/dashboard';
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'platform' : undefined
    );
    mockedGetMenus.mockResolvedValue(menuWithStoreAndPlatform);

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '受注一覧' })).toHaveAttribute(
      'href',
      '/store/2/orders'
    );
    expect(screen.getByRole('link', { name: '店舗一覧' })).toHaveAttribute(
      'href',
      '/platform/stores'
    );
  });

  it('path に storeId が無くても前回選択 cookie から店舗リンクを解決する', async () => {
    mockPathname = '/store/select';
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'platform-role') return 'platform';
      if (key === 'platform-store-id') return '3';
      return undefined;
    });
    mockedGetMenus.mockResolvedValue(menuWithStoreAndPlatform);

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '受注一覧' })).toHaveAttribute(
      'href',
      '/store/3/orders'
    );
  });

  it('path にも cookie にも storeId が無ければ店舗選択画面へ誘導する', async () => {
    mockPathname = '/store/select';
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'platform' : undefined
    );
    mockedGetMenus.mockResolvedValue(menuWithStoreAndPlatform);

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '受注一覧' })).toHaveAttribute(
      'href',
      '/store/select?next=%2Fstore%2Forders'
    );
  });
});
