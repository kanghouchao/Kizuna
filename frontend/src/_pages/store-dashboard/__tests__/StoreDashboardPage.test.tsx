import { render, screen } from '@testing-library/react';
import { useStoreContext } from '@/entities/user';
import StoreDashboardPage from '../ui/StoreDashboardPage';

jest.mock('@/entities/user', () => ({ useStoreContext: jest.fn() }));

const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const withStore = (status: 'PREPARING' | 'ACTIVE') =>
  mockedUseStoreContext.mockReturnValue({
    stores: [{ id: 2, name: '新宿店', status }],
    currentStoreId: '2',
    currentStore: { id: 2, name: '新宿店', status },
    loadFailed: false,
    reload: jest.fn(),
  });

describe('店舗のダッシュボード', () => {
  it.each([
    ['PREPARING', '準備中'],
    ['ACTIVE', '稼働中'],
  ] as const)('URL の店舗の名前と稼働状態（%s）を出す', (status, label) => {
    withStore(status);

    render(<StoreDashboardPage />);

    expect(screen.getByText('新宿店')).toBeInTheDocument();
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
