import { fireEvent, render, screen, within } from '@testing-library/react';
import { Sidebar } from '../Sidebar';
import { useMenuTrees, type MenuTree } from '@/entities/menu';
import { useStoreContext } from '@/entities/user';

let mockPathname = '/admin/dashboard';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/entities/menu', () => ({
  useMenuTrees: jest.fn(),
}));

jest.mock('@/entities/user', () => ({
  useStoreContext: jest.fn(),
}));

const mockedUseMenuTrees = useMenuTrees as jest.MockedFunction<typeof useMenuTrees>;
const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;

const STORES = [
  { id: 1, name: '銀座本店', short_name: '銀' },
  { id: 2, name: '新宿店', short_name: '新宿店' },
];

const reload = jest.fn();

function withStores(stores: typeof STORES | null, loadFailed = false) {
  mockedUseStoreContext.mockReturnValue({
    stores,
    currentStoreId: undefined,
    currentStore: undefined,
    loadFailed,
    reload,
  });
}

// seed の二つの木を縮めた写し。木は応答に含まれる者（その木の権限を持つ者）にだけ出る。
// 店舗の木の path は店舗の中のパス。
const item = (name: string, path: string) => ({ name, path, icon: 'HouseIcon' });
const dashboard = (path: string) => ({
  name: 'ダッシュボード',
  path,
  icon: 'HouseIcon',
  items: [],
});
const groupTree: MenuTree = {
  tree: 'GROUP',
  items: [
    dashboard('/admin/dashboard'),
    { name: '業務', items: [item('受注照会', '/admin/orders')] },
    {
      name: '組織',
      items: [item('店舗一覧', '/admin/stores'), item('従業員管理', '/admin/staff')],
    },
  ],
};
const storeTree: MenuTree = {
  tree: 'STORE',
  items: [
    dashboard('/dashboard'),
    { name: '業務', items: [item('予約・案件管理', '/orders')] },
    { name: '運用', items: [item('店舗情報', '/settings/profile')] },
  ],
};
const fullMenu = [groupTree, storeTree];
const storeOnlyMenu = [storeTree];
const groupOnlyMenu = [groupTree];

const railLink = (name: string) =>
  within(screen.getByRole('navigation', { name: '文脈' })).getByRole('link', { name });
const groupLink = () => screen.queryByRole('link', { name: 'グループ' });
const headings = () => screen.getAllByRole('heading').map(h => h.textContent);
const panelLinks = () =>
  screen
    .getAllByRole('navigation')
    .filter(nav => nav.getAttribute('aria-label') !== '文脈')
    .flatMap(nav => within(nav).getAllByRole('link'));

describe('Sidebar', () => {
  beforeEach(() => {
    mockPathname = '/admin/dashboard';
    mockedUseMenuTrees.mockReturnValue(fullMenu);
    withStores(STORES);
  });

  afterEach(() => jest.clearAllMocks());

  it('グループの木の path はそのまま、店舗の木の path は URL の店舗の中のパスとしてリンク先にする', () => {
    render(<Sidebar />);
    expect(screen.getByRole('link', { name: '受注照会' })).toHaveAttribute('href', '/admin/orders');

    mockPathname = '/store/2/orders';
    render(<Sidebar />);
    expect(screen.getByRole('link', { name: '予約・案件管理' })).toHaveAttribute(
      'href',
      '/store/2/orders'
    );
  });

  it.each([
    ['どの木も返らない', [] as MenuTree[]],
    ['店舗の木だけで店舗の画面にいない', storeOnlyMenu],
  ])('%s利用者には全員の着地先（ダッシュボード）への導線だけを出す', (_, trees) => {
    mockedUseMenuTrees.mockReturnValue(trees);
    withStores([STORES[0]]);

    render(<Sidebar />);

    expect(screen.getByRole('link', { name: 'ダッシュボード' })).toHaveAttribute(
      'href',
      '/admin/dashboard'
    );
    expect(screen.queryByRole('heading')).toBeNull();
  });

  it('メニューか授権店舗が解決するまでは項目もレールも出さない', () => {
    mockedUseMenuTrees.mockReturnValue(null);
    const { unmount } = render(<Sidebar />);
    expect(screen.queryByRole('link')).toBeNull();
    unmount();

    mockedUseMenuTrees.mockReturnValue(fullMenu);
    withStores(null);
    render(<Sidebar />);
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('授権店舗の取得に失敗したらグループの木だけで描き、失敗と再試行を出す', () => {
    withStores(null, true);

    render(<Sidebar />);

    expect(screen.getByRole('link', { name: '受注照会' })).toBeInTheDocument();
    expect(groupLink()).toBeNull();
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('店舗一覧の取得に失敗しました');
    fireEvent.click(within(alert).getByRole('button', { name: '再試行' }));
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('初回の失敗後に再試行している間も、描いたグループの項目を消さない', () => {
    withStores(null, true);
    const { rerender } = render(<Sidebar />);
    expect(screen.getByRole('link', { name: '受注照会' })).toBeInTheDocument();

    withStores(null, false);
    rerender(<Sidebar />);

    expect(screen.getByRole('link', { name: '受注照会' })).toBeInTheDocument();
  });

  it('取り直しに失敗したら直前の一覧のレールを残したまま、失敗と再試行を出す', () => {
    withStores(STORES, true);

    render(<Sidebar />);

    expect(railLink('新宿店')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('店舗一覧の取得に失敗しました');
  });

  it('取得に成功している間は失敗を出さない', () => {
    render(<Sidebar />);

    expect(screen.queryByRole('alert')).toBeNull();
  });

  describe('レール', () => {
    it('グループと各店舗が並び、それぞれの文脈のダッシュボードへのリンクになる', () => {
      render(<Sidebar />);

      expect(railLink('グループ')).toHaveAttribute('href', '/admin/dashboard');
      expect(railLink('銀座本店')).toHaveAttribute('href', '/store/1/dashboard');
      expect(railLink('新宿店')).toHaveAttribute('href', '/store/2/dashboard');
      expect(railLink('銀座本店')).toHaveAttribute('title', '銀座本店');
      expect(railLink('銀座本店')).toHaveTextContent('銀');
    });

    it('店舗の木だけを持つ利用者には「グループ」を出さない', () => {
      mockedUseMenuTrees.mockReturnValue(storeOnlyMenu);
      mockPathname = '/store/1/orders';

      render(<Sidebar />);

      expect(groupLink()).toBeNull();
      expect(railLink('新宿店')).toBeInTheDocument();
    });

    it('店舗が 1 件でグループの木も無ければレールを出さず、パネルは 256px', () => {
      mockedUseMenuTrees.mockReturnValue(storeOnlyMenu);
      withStores([STORES[0]]);
      mockPathname = '/store/1/orders';

      const { container } = render(<Sidebar />);

      expect(screen.getByRole('link', { name: '予約・案件管理' })).toBeInTheDocument();
      expect(screen.queryByRole('navigation', { name: '文脈' })).toBeNull();
      expect(container.querySelector('.w-64')).not.toBeNull();
    });

    it('グループの木だけを持ち授権店舗が無い利用者にはレールを出さない', () => {
      mockedUseMenuTrees.mockReturnValue(groupOnlyMenu);
      withStores([]);

      render(<Sidebar />);

      expect(screen.getByRole('link', { name: '店舗一覧' })).toBeInTheDocument();
      expect(screen.queryByRole('navigation', { name: '文脈' })).toBeNull();
    });

    it('店舗の木が無い利用者には授権店舗があっても店舗を出さず、グループの文脈になる', () => {
      mockedUseMenuTrees.mockReturnValue(groupOnlyMenu);

      render(<Sidebar />);

      expect(screen.getByRole('link', { name: '店舗一覧' })).toBeInTheDocument();
      expect(screen.queryByRole('navigation', { name: '文脈' })).toBeNull();
      expect(screen.getByText('グループ', { selector: 'span' })).toBeInTheDocument();
    });

    it('店舗 1 件と両方の木を持つ利用者にはレールが出て、パネルは 200px', () => {
      withStores([STORES[0]]);

      const { container } = render(<Sidebar />);

      expect(railLink('グループ')).toBeInTheDocument();
      expect(container.querySelector('.w-\\[200px\\]')).not.toBeNull();
    });
  });

  describe('文脈は URL が決める', () => {
    it('/admin の画面はグループの文脈で、グループの木の項目と見出しだけを出す', () => {
      render(<Sidebar />);

      expect(screen.getByRole('link', { name: '従業員管理' })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: '予約・案件管理' })).toBeNull();
      expect(headings()).toEqual(['業務', '組織']);
      expect(screen.getByText('グループ', { selector: 'span' })).toBeInTheDocument();
      expect(railLink('グループ')).toHaveAttribute('aria-current', 'page');
      expect(railLink('銀座本店')).not.toHaveAttribute('aria-current');
    });

    it('店舗の画面はその店舗の文脈で、店舗の木の項目と店名を出し、その店舗だけを選択表示にする', () => {
      mockPathname = '/store/2/orders/5/edit';

      render(<Sidebar />);

      expect(screen.getByRole('link', { name: '店舗情報' })).toHaveAttribute(
        'href',
        '/store/2/settings/profile'
      );
      expect(screen.queryByRole('link', { name: '受注照会' })).toBeNull();
      expect(headings()).toEqual(['業務', '運用']);
      expect(screen.getByText('新宿店', { selector: 'span' })).toBeInTheDocument();
      expect(railLink('新宿店')).toHaveAttribute('aria-current', 'page');
      expect(railLink('銀座本店')).not.toHaveAttribute('aria-current');
      expect(railLink('グループ')).not.toHaveAttribute('aria-current');
    });

    it('URL が変わると文脈も替わる（レールの選択を状態として持たない）', () => {
      mockPathname = '/store/1/orders';
      const { rerender } = render(<Sidebar />);
      expect(screen.getByText('銀座本店', { selector: 'span' })).toBeInTheDocument();

      mockPathname = '/admin/orders';
      rerender(<Sidebar />);
      expect(screen.getByRole('link', { name: '受注照会' })).toBeInTheDocument();
      expect(railLink('グループ')).toHaveAttribute('aria-current', 'page');

      mockPathname = '/store/2/dashboard';
      rerender(<Sidebar />);
      expect(railLink('新宿店')).toHaveAttribute('aria-current', 'page');
    });

    it('授権店舗に無い店舗の画面ではどの文脈も選ばず、着地先への導線だけを出す', () => {
      mockPathname = '/store/99/orders';

      render(<Sidebar />);

      expect(panelLinks().map(link => link.getAttribute('href'))).toEqual(['/admin/dashboard']);
      expect(railLink('グループ')).not.toHaveAttribute('aria-current');
    });

    it('ダッシュボードはどの文脈でも最上段にある', () => {
      render(<Sidebar />);
      expect(panelLinks()[0]).toHaveAttribute('href', '/admin/dashboard');

      mockPathname = '/store/1/orders';
      render(<Sidebar />);
      expect(screen.getAllByRole('link', { name: 'ダッシュボード' }).at(-1)).toHaveAttribute(
        'href',
        '/store/1/dashboard'
      );
    });

    it('項目はパスの接頭辞でなく、属する木の文脈に出る', () => {
      mockedUseMenuTrees.mockReturnValue([
        {
          tree: 'GROUP',
          items: [dashboard('/admin/dashboard'), { name: '業務', items: [item('X', '/admin/x')] }],
        },
        {
          tree: 'STORE',
          items: [dashboard('/dashboard'), { name: '業務', items: [item('Y', '/admin/y')] }],
        },
      ]);
      mockPathname = '/store/1/orders';

      render(<Sidebar />);

      expect(screen.getByRole('link', { name: 'Y' })).toHaveAttribute('href', '/store/1/admin/y');
      expect(screen.queryByRole('link', { name: 'X' })).toBeNull();
    });

    it('見出しと項目の順は seed のまま', () => {
      mockedUseMenuTrees.mockReturnValue([
        {
          tree: 'STORE',
          items: [
            dashboard('/dashboard'),
            { name: '業務', items: [item('A', '/a'), item('B', '/b')] },
            { name: '運用', items: [item('C', '/c')] },
            { name: '顧客', items: [item('D', '/d')] },
          ],
        },
      ]);
      withStores([STORES[0]]);
      mockPathname = '/store/1/a';

      render(<Sidebar />);

      expect(headings()).toEqual(['業務', '運用', '顧客']);
      expect(panelLinks().map(l => l.textContent)).toEqual(['ダッシュボード', 'A', 'B', 'C', 'D']);
    });

    it('長い店名は見出しで省略表示にし、全文を title に持つ', () => {
      const longName = '長'.repeat(200);
      withStores([{ id: 1, name: longName, short_name: '長' }]);
      mockPathname = '/store/1/orders';

      render(<Sidebar />);

      const heading = screen.getByText(longName, { selector: 'span' });
      expect(heading).toHaveClass('truncate');
      expect(heading).toHaveAttribute('title', longName);
    });

    it('現在地の項目だけが他項目と異なる見えになる', () => {
      mockPathname = '/store/1/settings/profile';

      render(<Sidebar />);

      const active = screen.getByRole('link', { name: '店舗情報' });
      const inactive = screen.getByRole('link', { name: '予約・案件管理' });
      expect(active.className).not.toBe(inactive.className);
    });
  });
});
