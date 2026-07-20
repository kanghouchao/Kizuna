import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import StoreSelectPage from '../StoreSelectPage';
import { useAuthorizedStores } from '@/entities/user';

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

// 授権店舗の解決（能力ゲート・403 フォールバック含む）は useAuthorizedStores の責務で、
// その全ケースは useAuthorizedStores.test.ts で検証する。ここでは hook 出力に対する
// StoreSelectPage 自身の分岐（読み込み中 / 0件 / 1件自動遷移 / 複数件選択）を検証する（#413 Fix7）。
jest.mock('@/entities/user', () => ({
  useAuthorizedStores: jest.fn(),
}));

const mockedUseAuthorizedStores = useAuthorizedStores as jest.MockedFunction<
  typeof useAuthorizedStores
>;

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
    mockedUseAuthorizedStores.mockReturnValue(null);

    render(<StoreSelectPage />);

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('授権店舗が0件のとき、遷移せず「アクセス可能な店舗がありません」を表示する', () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedUseAuthorizedStores.mockReturnValue([]);

    render(<StoreSelectPage />);

    expect(screen.getByText('アクセス可能な店舗がありません')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('授権店舗が1件のとき、選択UIを出さず既定の next に storeId を埋めて自動遷移する', async () => {
    window.history.pushState({}, '', '/store/select');
    mockedUseAuthorizedStores.mockReturnValue([{ id: 5, name: '店舗A' }]);

    render(<StoreSelectPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/dashboard'));
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });

  it('授権店舗が複数件のとき、一覧を表示しクリックで next に storeId を埋めて遷移する', () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedUseAuthorizedStores.mockReturnValue([
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
