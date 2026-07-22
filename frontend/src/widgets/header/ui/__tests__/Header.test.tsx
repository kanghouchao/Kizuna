import { render, screen, fireEvent } from '@testing-library/react';
import { Header } from '../Header';
import { useStoreContext } from '@/entities/user';
import { isStoreDomain } from '@/shared/lib';
import type { PlatformStore } from '@/entities/user';

// Headless UI の Menu は開閉時に ResizeObserver を使うが jsdom には無いため最小スタブを差す。
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
global.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;

// 現在店舗・授権店舗・切替は店舗コンテキストが担い、その全ケースは StoreContext.test.tsx で検証する。
// ここでは context 出力（stores / currentStoreId）に対する Header 自身の表示条件・ラベル・
// accountHref・切替クリックの委譲を検証する（#428）。
jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: jest.fn() }),
  useStoreContext: jest.fn(),
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  isStoreDomain: jest.fn(() => false),
}));

const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const mockedIsStoreDomain = isStoreDomain as jest.MockedFunction<typeof isStoreDomain>;
const mockSwitchStore = jest.fn();

function withContext(stores: PlatformStore[] | null, currentStoreId?: string) {
  mockedUseStoreContext.mockReturnValue({
    stores,
    storeBridge: stores === null ? null : stores.length > 0,
    currentStoreId,
    switchStore: mockSwitchStore,
  });
}

describe('Header 店舗切替の常設化（#413 / 店舗コンテキスト集約 #428）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedIsStoreDomain.mockReturnValue(false);
    withContext([]);
  });

  it('授権店舗が0件なら店舗切替は表示されない', () => {
    withContext([]);

    render(<Header />);

    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });

  it('授権店舗の解決中（null）は店舗切替を表示しない', () => {
    withContext(null);

    render(<Header />);

    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
  });

  it('授権店舗が1件なら現在店舗名で店舗切替が表示される', () => {
    withContext([{ id: 1, name: '店舗A' }], '1');

    render(<Header />);

    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
  });

  it('店舗切替クリックは context の switchStore に店舗 id を委譲する', () => {
    withContext(
      [
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ],
      '1'
    );

    render(<Header />);
    fireEvent.click(screen.getByRole('button', { name: '店舗A' }));
    fireEvent.click(screen.getByRole('menuitem', { name: '店舗B' }));

    expect(mockSwitchStore).toHaveBeenCalledWith(2);
  });

  it('店舗別ドメイン経由ではアカウント設定リンクが currentStoreId を含む店舗ルートを指す（#413 Fix2）', () => {
    mockedIsStoreDomain.mockReturnValue(true);
    withContext([], '2');

    render(<Header />);

    expect(screen.getByRole('link', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/store/2/settings/account'
    );
  });

  it('店舗別ドメイン経由でも currentStoreId が未確定ならアカウント設定リンクは platform 側へ fallback する（#413 Fix3）', () => {
    mockedIsStoreDomain.mockReturnValue(true);
    withContext([], undefined);

    render(<Header />);

    expect(screen.getByRole('link', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/platform/settings/account'
    );
  });

  it('currentStoreId が変化するとラベルが新しい店舗名へ追随する（#413 Fix1）', () => {
    withContext(
      [
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ],
      '2'
    );

    const { rerender } = render(<Header />);

    expect(screen.getByRole('button', { name: '店舗B' })).toBeInTheDocument();

    withContext(
      [
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ],
      '1'
    );
    rerender(<Header />);

    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '店舗B' })).not.toBeInTheDocument();
  });
});
