import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { menuApi } from '@/entities/menu';
import { useAuth, useStoreContext } from '@/entities/user';
import { getPlatformStoreId, setPlatformStore } from '@/shared/lib';
import StoreEntryPage from '../StoreEntryPage';

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ replace: mockReplace }) }));

jest.mock('@/entities/menu', () => ({ menuApi: { getMenus: jest.fn() } }));
jest.mock('@/entities/user', () => ({ useAuth: jest.fn(), useStoreContext: jest.fn() }));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  getPlatformStoreId: jest.fn(),
  setPlatformStore: jest.fn(),
}));
jest.mock('react-hot-toast', () => ({ toast: { error: jest.fn() } }));

const mockedMenuApi = menuApi as jest.Mocked<typeof menuApi>;
const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const mockedGetStoreId = getPlatformStoreId as jest.MockedFunction<typeof getPlatformStoreId>;
const mockedSetStore = setPlatformStore as jest.MockedFunction<typeof setPlatformStore>;
const mockedToast = toast as jest.Mocked<typeof toast>;

const logout = jest.fn();

/** 統合メニューは両コンソールの節を返し、プラットフォーム節が先に並ぶ（sort_order 由来）。 */
const bothConsolesMenu = [
  { name: 'メイン', items: [{ name: 'ダッシュボード', path: '/platform/dashboard' }] },
  { name: '業務管理', items: [{ name: '予約・案件管理', path: '/store/orders' }] },
  { name: 'CRM', items: [{ name: '顧客一覧', path: '/store/customers' }] },
];

const reload = jest.fn();

const storeContext = (override: Partial<ReturnType<typeof useStoreContext>> = {}) => ({
  stores: [{ id: 5, name: '店舗A' }],
  storeBridge: true,
  currentStoreId: undefined,
  loadFailed: false,
  reload,
  switchStore: jest.fn(),
  ...override,
});

beforeEach(() => {
  jest.clearAllMocks();
  window.history.pushState({}, '', '/store/entry');
  mockedUseAuth.mockReturnValue({ logout });
  mockedUseStoreContext.mockReturnValue(storeContext());
  mockedMenuApi.getMenus.mockResolvedValue(bothConsolesMenu);
  mockedGetStoreId.mockReturnValue(undefined);
});

describe('店舗コンソール入口', () => {
  it('読み込み中は遷移せずローディングだけを出す', () => {
    mockedUseStoreContext.mockReturnValue(storeContext({ stores: null, storeBridge: null }));

    render(<StoreEntryPage />);

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('メニュー先頭の店舗スコープ項目へ storeId を埋めて遷移する', async () => {
    render(<StoreEntryPage />);

    // プラットフォーム節が先に並んでいても /platform/dashboard へは吸われない
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/orders'));
    expect(mockedSetStore).toHaveBeenCalledWith('5');
  });

  it('next クエリがあればメニューを引かずそこへ遷移する', async () => {
    window.history.pushState({}, '', '/store/entry?next=%2Fstore%2Fcasts');

    render(<StoreEntryPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/casts'));
    expect(mockedMenuApi.getMenus).not.toHaveBeenCalled();
  });

  it.each(['https://evil.example/x', '//evil.example/x', '/platform/dashboard'])(
    'next が店舗スコープ外（%s）ならそれを捨ててメニュー由来の着地先へ行く',
    async (hostile: string) => {
      // next は利用者が任意に書ける。素通しすると外部サイトへの誘導になる。
      window.history.pushState({}, '', `/store/entry?next=${encodeURIComponent(hostile)}`);

      render(<StoreEntryPage />);

      await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/orders'));
    }
  );

  it('前回選択の店舗が授権集合に含まれていればそれを優先する', async () => {
    mockedUseStoreContext.mockReturnValue(
      storeContext({
        stores: [
          { id: 5, name: '店舗A' },
          { id: 9, name: '店舗B' },
        ],
      })
    );
    mockedGetStoreId.mockReturnValue('9');

    render(<StoreEntryPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/9/orders'));
  });

  it('前回選択が授権集合から外れていたら先頭店舗へ落とす', async () => {
    mockedGetStoreId.mockReturnValue('999');

    render(<StoreEntryPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/orders'));
  });

  it('店舗コンソール資格が無い利用者はログアウトさせず平台へ返す', async () => {
    // HQ 管理者は store_bridge=false かつ授権店舗が空なのが正常。
    // ここでセッションを捨てると正当な利用者を追い出すログアウト事故になる。
    mockedUseStoreContext.mockReturnValue(storeContext({ stores: [], storeBridge: false }));

    render(<StoreEntryPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/platform/dashboard'));
    expect(logout).not.toHaveBeenCalled();
    expect(mockedToast.error).not.toHaveBeenCalled();
  });

  it('店舗コンソール資格はあるが授権店舗が 0 件なら fail-closed でログアウトする', async () => {
    mockedUseStoreContext.mockReturnValue(storeContext({ stores: [], storeBridge: true }));

    render(<StoreEntryPage />);

    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
    expect(mockedToast.error).toHaveBeenCalledWith(
      'アクセス可能な店舗がありません。管理者にお問い合わせください'
    );
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('店舗スコープのメニュー項目が 1 つも無ければ fail-closed でログアウトする', async () => {
    mockedMenuApi.getMenus.mockResolvedValue([
      { name: 'メイン', items: [{ name: 'ダッシュボード', path: '/platform/dashboard' }] },
    ]);

    render(<StoreEntryPage />);

    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
    // 店舗はあるので「店舗が無い」と言ってはいけない。管理者が直す先が授権ではなくロールの権限のため。
    expect(mockedToast.error).toHaveBeenCalledWith(
      'アクセスできる画面がありません。管理者にお問い合わせください'
    );
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('文脈の取得失敗はログアウトも平台側への送出もせず再試行を出す', async () => {
    // me()/stores() の失敗を空一覧・資格なしへ畳むと、通信障害が「授権店舗ゼロ」（ログアウト）や
    // 「資格なし」（平台側へ送出 → 守衛が入口へ弾き返す往復）に化ける。
    mockedUseStoreContext.mockReturnValue(
      storeContext({ stores: null, storeBridge: null, loadFailed: true })
    );

    render(<StoreEntryPage />);

    expect(await screen.findByRole('button', { name: '再試行' })).toBeInTheDocument();
    expect(logout).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('文脈の取得失敗の再試行は provider に取り直させる', async () => {
    mockedUseStoreContext.mockReturnValue(
      storeContext({ stores: null, storeBridge: null, loadFailed: true })
    );

    render(<StoreEntryPage />);
    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));

    expect(reload).toHaveBeenCalledTimes(1);
    expect(mockedMenuApi.getMenus).not.toHaveBeenCalled();
  });

  it('入口ルート自身は next として受け付けずメニュー由来の着地先へ回す', async () => {
    // 受け付けると自分自身へ遷移し、解決済みの旗が立っているので読み込み中のまま止まる。
    window.history.pushState({}, '', '/store/entry?next=%2Fstore%2Fentry');

    render(<StoreEntryPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/orders'));
  });

  it('入口ルート配下も next として受け付けない', async () => {
    // /store/entry/foo は実在せず、storeId も埋まらないので解決すると 404 になる。
    window.history.pushState({}, '', '/store/entry?next=%2Fstore%2Fentry%2Ffoo');

    render(<StoreEntryPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/orders'));
  });

  it('廃止済みルートは next として復元せずメニュー由来の着地先へ回す', async () => {
    // 復元すると実在しない /store/5/select へ飛ばして 404 になる。
    window.history.pushState({}, '', '/store/entry?next=%2Fstore%2Fselect');

    render(<StoreEntryPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/orders'));
  });

  it('メニュー取得の失敗はログアウトさせず再試行を出す', async () => {
    // 取得失敗は「行ける場所が無い」というサーバの答えではない。ここで畳むと、
    // メニュー障害時にサイドバーが出す入口リンク自体がログアウトボタンになる。
    mockedMenuApi.getMenus.mockRejectedValue(new Error('boom'));

    render(<StoreEntryPage />);

    expect(await screen.findByRole('button', { name: '再試行' })).toBeInTheDocument();
    expect(logout).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('再試行で取得が回復すれば通常どおり着地する', async () => {
    mockedMenuApi.getMenus.mockRejectedValueOnce(new Error('boom'));

    render(<StoreEntryPage />);
    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/orders'));
    expect(logout).not.toHaveBeenCalled();
  });
});
