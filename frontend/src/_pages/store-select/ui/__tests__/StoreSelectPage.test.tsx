import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import StoreSelectPage from '../StoreSelectPage';
import { platformAuthApi } from '@/entities/user';

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock('@/entities/user', () => ({
  platformAuthApi: { stores: jest.fn() },
}));

const mockedStores = platformAuthApi.stores as jest.MockedFunction<typeof platformAuthApi.stores>;

describe('StoreSelectPage（店舗未選択時の懒惰トリガー選択画面 #413）', () => {
  const originalHref = window.location.href;

  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterEach(() => {
    window.history.pushState({}, '', originalHref);
  });

  it('授権店舗が0件のとき、遷移せず「アクセス可能な店舗がありません」を表示する', async () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedStores.mockResolvedValue([]);

    render(<StoreSelectPage />);

    expect(await screen.findByText('アクセス可能な店舗がありません')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('授権店舗が1件のとき、選択UIを出さず既定の next に storeId を埋めて自動遷移する', async () => {
    window.history.pushState({}, '', '/store/select');
    mockedStores.mockResolvedValue([{ id: 5, name: '店舗A' }]);

    render(<StoreSelectPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/dashboard'));
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });

  it('授権店舗が複数件のとき、一覧を表示しクリックで next に storeId を埋めて遷移する', async () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedStores.mockResolvedValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<StoreSelectPage />);

    const storeB = await screen.findByRole('button', { name: '店舗B' });
    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();

    fireEvent.click(storeB);

    expect(mockReplace).toHaveBeenCalledWith('/store/2/orders');
  });
});
