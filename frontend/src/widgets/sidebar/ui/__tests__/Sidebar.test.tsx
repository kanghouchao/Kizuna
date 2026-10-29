import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Sidebar } from '../Sidebar';
import { menuApi } from '@/entities/menu';
import { useStoreContext } from '@/entities/user';

let mockPathname = '/admin/dashboard';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/entities/menu', () => ({
  menuApi: { getMenus: jest.fn() },
}));

jest.mock('@/entities/user', () => ({
  useStoreContext: jest.fn(),
}));

const mockedGetMenus = menuApi.getMenus as jest.Mock;
const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const switchStore = jest.fn();

const STORES = [
  { id: 1, name: '銀座本店', short_name: '銀' },
  { id: 2, name: '新宿店', short_name: '新宿店' },
];

function withStores(stores: typeof STORES | null, currentStoreId = '1') {
  mockedUseStoreContext.mockReturnValue({
    stores,
    currentStoreId,
    currentStore: stores?.find(store => String(store.id) === currentStoreId),
    loadFailed: false,
    reload: jest.fn(),
    switchStore,
  });
}

// seed の見出し構成を縮めた写し。業務・キャスト・顧客は両文脈に項目を持ち、組織はグループだけ。
const item = (name: string, path: string) => ({ name, path, icon: 'HouseIcon' });
const fullMenu = [
  { name: 'ダッシュボード', path: '/admin/dashboard', icon: 'HouseIcon', items: [] },
  {
    name: '業務',
    items: [item('受注照会', '/admin/orders'), item('予約・案件管理', '/store/orders')],
  },
  { name: '組織', items: [item('店舗一覧', '/admin/stores'), item('従業員管理', '/admin/staff')] },
  { name: '運用', items: [item('店舗情報', '/store/profile')] },
];
const storeOnlyMenu = [
  fullMenu[0],
  { name: '業務', items: [item('予約・案件管理', '/store/orders')] },
  fullMenu[3],
];
const groupOnlyMenu = [fullMenu[0], fullMenu[2]];

const rail = () => screen.queryByRole('button', { name: 'グループ' });
const headings = () => screen.getAllByRole('heading').map(h => h.textContent);

describe('Sidebar', () => {
  beforeEach(() => {
    mockPathname = '/admin/dashboard';
    mockedGetMenus.mockResolvedValue(fullMenu);
    withStores(STORES);
  });

  afterEach(() => jest.clearAllMocks());

  it('メニューの path をそのままリンク先にする', async () => {
    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '予約・案件管理' })).toHaveAttribute(
      'href',
      '/store/orders'
    );
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
    expect(screen.queryByRole('heading')).toBeNull();
    expect(rail()).toBeNull();
  });

  it('授権店舗が解決するまでは項目もレールも出さない', async () => {
    withStores(null);

    render(<Sidebar />);

    await waitFor(() => expect(menuApi.getMenus).toHaveBeenCalled());
    expect(screen.queryByRole('link')).toBeNull();
    expect(rail()).toBeNull();
  });

  it('授権店舗の取得に失敗したらグループ級の項目だけで描く', async () => {
    mockedUseStoreContext.mockReturnValue({
      stores: null,
      currentStoreId: undefined,
      currentStore: undefined,
      loadFailed: true,
      reload: jest.fn(),
      switchStore,
    });

    render(<Sidebar />);

    expect(await screen.findByRole('link', { name: '受注照会' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '予約・案件管理' })).toBeNull();
  });

  describe('レール', () => {
    it('グループと各店舗が並び、店舗ボタンは略称を表示して店名を名乗る', async () => {
      render(<Sidebar />);

      await screen.findByRole('link', { name: '予約・案件管理' });
      const group = screen.getByRole('button', { name: 'グループ' });
      const ginza = screen.getByRole('button', { name: '銀座本店' });
      expect(group).toHaveAttribute('title', 'グループ');
      expect(ginza).toHaveAttribute('title', '銀座本店');
      expect(ginza).toHaveTextContent('銀');
      expect(screen.getByRole('button', { name: '新宿店' })).toHaveTextContent('新宿店');
    });

    it('グループ級の項目が無い利用者には「グループ」を出さない', async () => {
      mockedGetMenus.mockResolvedValue(storeOnlyMenu);

      render(<Sidebar />);

      await screen.findByRole('link', { name: '予約・案件管理' });
      expect(rail()).toBeNull();
      expect(screen.getByRole('button', { name: '銀座本店' })).toBeInTheDocument();
    });

    it('店舗が 1 件でグループ級の項目も無ければレールを出さず、パネルは 256px', async () => {
      mockedGetMenus.mockResolvedValue(storeOnlyMenu);
      withStores([STORES[0]]);

      const { container } = render(<Sidebar />);

      await screen.findByRole('link', { name: '予約・案件管理' });
      expect(screen.queryByRole('button')).toBeNull();
      expect(container.querySelector('.w-64')).not.toBeNull();
      expect(container.querySelector('.w-\\[200px\\]')).toBeNull();
    });

    it('グループ級の項目だけを持つ利用者にはレールを出さない', async () => {
      mockedGetMenus.mockResolvedValue(groupOnlyMenu);

      render(<Sidebar />);

      await screen.findByRole('link', { name: '店舗一覧' });
      expect(screen.queryByRole('button')).toBeNull();
    });

    it('店舗 1 件とグループ級の項目を持つ利用者にはレールが出て、パネルは 200px', async () => {
      withStores([STORES[0]]);

      const { container } = render(<Sidebar />);

      await screen.findByRole('link', { name: '予約・案件管理' });
      expect(rail()).not.toBeNull();
      expect(container.querySelector('.w-\\[200px\\]')).not.toBeNull();
    });
  });

  describe('パネル', () => {
    it('既定では現在店舗の文脈で、店舗級の項目と見出しだけを出す', async () => {
      render(<Sidebar />);

      await screen.findByRole('link', { name: '予約・案件管理' });
      expect(screen.getByRole('link', { name: '店舗情報' })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: '受注照会' })).toBeNull();
      expect(screen.queryByRole('link', { name: '店舗一覧' })).toBeNull();
      expect(headings()).toEqual(['業務', '運用']);
      expect(screen.getByText('銀座本店', { selector: 'span' })).toBeInTheDocument();
    });

    it('グループを選ぶとグループ級の項目と見出しだけを出す（組織は店舗の文脈に出ない）', async () => {
      render(<Sidebar />);
      await screen.findByRole('link', { name: '予約・案件管理' });

      fireEvent.click(screen.getByRole('button', { name: 'グループ' }));

      expect(screen.getByRole('link', { name: '受注照会' })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: '従業員管理' })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: '予約・案件管理' })).toBeNull();
      expect(headings()).toEqual(['業務', '組織']);
      expect(screen.getByText('グループ', { selector: 'span' })).toBeInTheDocument();
    });

    it('ダッシュボードはどの文脈でも最上段にある', async () => {
      render(<Sidebar />);
      await screen.findByRole('link', { name: '予約・案件管理' });

      const firstLink = () => within(screen.getByRole('navigation')).getAllByRole('link')[0];
      expect(firstLink()).toHaveTextContent('ダッシュボード');
      fireEvent.click(screen.getByRole('button', { name: 'グループ' }));
      expect(firstLink()).toHaveTextContent('ダッシュボード');
    });

    it('見出しと項目の順は seed のまま', async () => {
      mockedGetMenus.mockResolvedValue([
        fullMenu[0],
        { name: '業務', items: [item('A', '/store/a'), item('B', '/store/b')] },
        { name: '運用', items: [item('C', '/store/c')] },
        { name: '顧客', items: [item('D', '/store/d')] },
      ]);
      withStores([STORES[0]]);

      render(<Sidebar />);

      await screen.findByRole('link', { name: 'A' });
      expect(headings()).toEqual(['業務', '運用', '顧客']);
      expect(screen.getAllByRole('link').map(l => l.textContent)).toEqual([
        'ダッシュボード',
        'A',
        'B',
        'C',
        'D',
      ]);
    });

    it('長い店名は見出しで省略表示にし、全文を title に持つ', async () => {
      const longName = '長'.repeat(200);
      withStores([{ id: 1, name: longName, short_name: '長' }]);

      render(<Sidebar />);

      await screen.findByRole('link', { name: '予約・案件管理' });
      const heading = screen.getByText(longName, { selector: 'span' });
      expect(heading).toHaveClass('truncate');
      expect(heading).toHaveAttribute('title', longName);
    });

    it('現在地の項目だけが他項目と異なる見えになる', async () => {
      mockPathname = '/store/profile';

      render(<Sidebar />);

      const active = await screen.findByRole('link', { name: '店舗情報' });
      const inactive = screen.getByRole('link', { name: '予約・案件管理' });
      expect(active.className).not.toBe(inactive.className);
    });
  });

  describe('文脈の切替', () => {
    it('店舗を選ぶと switchStore を呼び、パネルは遷移なしに店舗の文脈へ替わる', async () => {
      render(<Sidebar />);
      await screen.findByRole('link', { name: '予約・案件管理' });
      fireEvent.click(screen.getByRole('button', { name: 'グループ' }));
      expect(screen.queryByRole('link', { name: '予約・案件管理' })).toBeNull();

      fireEvent.click(screen.getByRole('button', { name: '新宿店' }));

      expect(switchStore).toHaveBeenCalledWith(2);
      expect(screen.getByRole('link', { name: '予約・案件管理' })).toBeInTheDocument();
    });

    it('選択中の文脈だけが選択表示（aria-pressed）になる', async () => {
      render(<Sidebar />);
      await screen.findByRole('link', { name: '予約・案件管理' });

      expect(screen.getByRole('button', { name: '銀座本店' })).toHaveAttribute(
        'aria-pressed',
        'true'
      );
      expect(screen.getByRole('button', { name: '新宿店' })).toHaveAttribute(
        'aria-pressed',
        'false'
      );
      expect(screen.getByRole('button', { name: 'グループ' })).toHaveAttribute(
        'aria-pressed',
        'false'
      );

      fireEvent.click(screen.getByRole('button', { name: 'グループ' }));

      expect(screen.getByRole('button', { name: 'グループ' })).toHaveAttribute(
        'aria-pressed',
        'true'
      );
      expect(screen.getByRole('button', { name: '銀座本店' })).toHaveAttribute(
        'aria-pressed',
        'false'
      );
    });

    it('画面を行き来しても選んだ文脈は保たれる', async () => {
      const { rerender } = render(<Sidebar />);
      await screen.findByRole('link', { name: '予約・案件管理' });
      fireEvent.click(screen.getByRole('button', { name: 'グループ' }));

      mockPathname = '/store/orders';
      rerender(<Sidebar />);

      expect(screen.getByRole('link', { name: '受注照会' })).toBeInTheDocument();
    });
  });
});
