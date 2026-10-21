import { render, screen, waitFor, within } from '@testing-library/react';
import { Sidebar } from '../Sidebar';
import { menuApi } from '@/entities/menu';

let mockPathname = '/store/orders';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/entities/menu', () => ({
  menuApi: { getMenus: jest.fn() },
}));

const mockedGetMenus = menuApi.getMenus as jest.Mock;

// 店舗スコープ項目とグループ級項目を1つずつ持つメニュー（href 解決の検証用）。
const menuWithStoreAndAdmin = [
  {
    name: 'メイン',
    items: [
      { name: '受注一覧', path: '/store/orders', icon: 'HouseIcon' },
      { name: '店舗一覧', path: '/admin/stores', icon: 'HouseIcon' },
    ],
  },
];

describe('Sidebar', () => {
  beforeEach(() => {
    mockPathname = '/store/orders';
    mockedGetMenus.mockResolvedValue([]);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('メニューの path をそのままリンク先にする（店舗級の項目にも店舗 id を埋めない）', async () => {
    mockedGetMenus.mockResolvedValue(menuWithStoreAndAdmin);

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '受注一覧' })).toHaveAttribute(
      'href',
      '/store/orders'
    );
    expect(screen.getByRole('link', { name: '店舗一覧' })).toHaveAttribute('href', '/admin/stores');
    expect(menuApi.getMenus).toHaveBeenCalledTimes(1);
  });

  it('メニュー取得が失敗したら全員の着地先（ダッシュボード）への導線だけを出す', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    mockedGetMenus.mockRejectedValue(new Error('network'));

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: 'ダッシュボード' })).toHaveAttribute(
      'href',
      '/admin/dashboard'
    );
  });

  // 現在地の項目だけが他と異なる見えを持つ、という関係だけを見る。
  // 具体的なクラス名に依存しないので配色の作り替えを跨いでも意味が変わらない。
  it('現在地の項目だけが他項目と異なる見えになる', async () => {
    mockPathname = '/admin/stores';
    mockedGetMenus.mockResolvedValue(menuWithStoreAndAdmin);

    render(<Sidebar />);

    const active = await screen.findByRole('link', { name: '店舗一覧' });
    const inactive = screen.getByRole('link', { name: '受注一覧' });

    expect(active.className).not.toBe(inactive.className);
  });

  it('現在地でない画面では全項目が同じ見えになる', async () => {
    mockPathname = '/admin/dashboard';
    mockedGetMenus.mockResolvedValue(menuWithStoreAndAdmin);

    render(<Sidebar />);

    const first = await screen.findByRole('link', { name: '受注一覧' });
    const second = screen.getByRole('link', { name: '店舗一覧' });

    expect(first.className).toBe(second.className);
  });

  // グループ見出しと配下項目の親子関係。折りたたみ機構は現状無く、
  // 全項目が操作なしで見えていることが e2e の前提でもある。
  it('グループ見出しの配下に当該グループの項目が展開されている', async () => {
    mockedGetMenus.mockResolvedValue([
      ...menuWithStoreAndAdmin,
      { name: '設定', items: [{ name: '店舗情報', path: '/store/profile', icon: 'SettingsIcon' }] },
    ]);

    render(<Sidebar />);

    const mainGroup = (await screen.findByText('メイン')).parentElement as HTMLElement;
    const settingsGroup = screen.getByText('設定').parentElement as HTMLElement;

    expect(within(mainGroup).getByRole('link', { name: '受注一覧' })).toBeVisible();
    expect(within(mainGroup).getByRole('link', { name: '店舗一覧' })).toBeVisible();
    expect(within(mainGroup).queryByRole('link', { name: '店舗情報' })).toBeNull();
    expect(within(settingsGroup).getByRole('link', { name: '店舗情報' })).toBeVisible();
  });

  it('識別ラベルは画面の区別なく Kizuna を掲げる', async () => {
    render(<Sidebar />);

    expect(await screen.findByText('Kizuna')).toBeVisible();
    await waitFor(() => expect(menuApi.getMenus).toHaveBeenCalled());
  });
});
