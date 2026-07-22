import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import StoreSelectPage from '../StoreSelectPage';
import { useStoreContext } from '@/entities/user';
import type { PlatformStore } from '@/entities/user';

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

// 授権店舗の解決（me()+stores()）は店舗コンテキストの責務で、その全ケースは StoreContext.test.tsx で
// 検証する。ここでは context 出力（stores）に対する StoreSelectPage 自身の分岐
//（読み込み中 / 0件 / 1件自動遷移 / 複数件選択）を検証する（#428）。
jest.mock('@/entities/user', () => ({
  useStoreContext: jest.fn(),
}));

const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;

const withStores = (stores: PlatformStore[] | null) =>
  mockedUseStoreContext.mockReturnValue({
    stores,
    storeBridge: null,
    currentStoreId: undefined,
    switchStore: jest.fn(),
  });

describe('StoreSelectPage（店舗未選択時の懒惰トリガー選択画面 #413）', () => {
  const originalHref = window.location.href;

  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterEach(() => {
    window.history.pushState({}, '', originalHref);
  });

  it('授権店舗の解決中（null）は読み込み中を表示し遷移しない', () => {
    window.history.pushState({}, '', '/store/select');
    withStores(null);

    render(<StoreSelectPage />);

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('授権店舗が0件のとき、遷移せず「アクセス可能な店舗がありません」を表示する', () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    withStores([]);

    render(<StoreSelectPage />);

    expect(screen.getByText('アクセス可能な店舗がありません')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('授権店舗が1件のとき、選択UIを出さず既定の next に storeId を埋めて自動遷移する', async () => {
    window.history.pushState({}, '', '/store/select');
    withStores([{ id: 5, name: '店舗A' }]);

    render(<StoreSelectPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/dashboard'));
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });

  it('授権店舗が複数件のとき、一覧を表示しクリックで next に storeId を埋めて遷移する', () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    withStores([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<StoreSelectPage />);

    const storeB = screen.getByRole('button', { name: '店舗B' });
    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();

    fireEvent.click(storeB);

    expect(mockReplace).toHaveBeenCalledWith('/store/2/orders');
  });
});
