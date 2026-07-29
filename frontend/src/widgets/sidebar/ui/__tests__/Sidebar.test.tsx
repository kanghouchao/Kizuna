import { render, screen, waitFor, within } from '@testing-library/react';
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
      { name: '受注一覧', path: '/store/orders', icon: 'HouseIcon' },
      { name: '店舗一覧', path: '/platform/stores', icon: 'HouseIcon' },
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
    mockPathname = '/store/entry';
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

  it('path にも cookie にも storeId が無ければ店舗入口画面へ誘導する', async () => {
    mockPathname = '/store/entry';
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'platform' : undefined
    );
    mockedGetMenus.mockResolvedValue(menuWithStoreAndPlatform);

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '受注一覧' })).toHaveAttribute(
      'href',
      '/store/entry?next=%2Fstore%2Forders'
    );
  });

  it('メニュー取得が失敗したときの店舗コンソール導線に storeId を埋めない', async () => {
    // 障害時に出る唯一の導線なので、/store/{id}/entry へ変換されると 404 になり退路が消える。
    mockPathname = '/store/5/orders';
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'store' : undefined
    );
    mockedGetMenus.mockRejectedValue(new Error('boom'));

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '店舗コンソール' })).toHaveAttribute(
      'href',
      '/store/entry'
    );
  });

  // 現在地の項目だけが他と異なる見えを持つ、という関係だけを見る。
  // 具体的なクラス名に依存しないので配色の作り替えを跨いでも意味が変わらない。
  it('現在地の項目だけが他項目と異なる見えになる', async () => {
    mockPathname = '/platform/stores';
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'platform' : undefined
    );
    mockedGetMenus.mockResolvedValue(menuWithStoreAndPlatform);

    render(<Sidebar />);

    const active = await screen.findByRole('link', { name: '店舗一覧' });
    const inactive = screen.getByRole('link', { name: '受注一覧' });

    expect(active.className).not.toBe(inactive.className);
  });

  it('現在地でない画面では全項目が同じ見えになる', async () => {
    mockPathname = '/platform/dashboard';
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'platform' : undefined
    );
    mockedGetMenus.mockResolvedValue(menuWithStoreAndPlatform);

    render(<Sidebar />);

    const first = await screen.findByRole('link', { name: '受注一覧' });
    const second = screen.getByRole('link', { name: '店舗一覧' });

    expect(first.className).toBe(second.className);
  });

  // グループ見出しと配下項目の親子関係。折りたたみ機構は現状無く、
  // 全項目が操作なしで見えていることが e2e の前提でもある。
  it('グループ見出しの配下に当該グループの項目が展開されている', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'platform' : undefined
    );
    mockedGetMenus.mockResolvedValue([
      ...menuWithStoreAndPlatform,
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

  it('コンソール種別に応じた識別ラベルを掲げる', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'platform-role' ? 'store' : undefined
    );

    render(<Sidebar />);

    expect(await screen.findByText('STORE')).toBeVisible();
    expect(screen.queryByText('PLATFORM')).toBeNull();
  });
});
