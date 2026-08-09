import { fireEvent, render, screen, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import CustomerEditPage from '../ui/CustomerEditPage';
import { customerApi } from '@/entities/customer';
import { orderApi } from '@/entities/order';

const mockPush = jest.fn();

jest.mock('@/entities/customer', () => ({
  customerApi: {
    get: jest.fn(),
    update: jest.fn(),
    linkMember: jest.fn(),
    unlinkMember: jest.fn(),
    memberLinkHistory: jest.fn(),
  },
}));

jest.mock('@/entities/order', () => ({
  orderApi: { list: jest.fn() },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => ({ storeId: '1', id: 'cus-1' }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedCustomerApi = customerApi as jest.Mocked<typeof customerApi>;
const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

const customer = {
  id: 'cus-1',
  name: '山田太郎',
  points: 120,
  created_at: '2026-07-01T00:00:00Z',
  updated_at: '2026-07-01T00:00:00Z',
};

const emptyOrderPage = { rows: [], page: 0, pageCount: 1, total: 0 };

describe('顧客編集ページの取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCustomerApi.memberLinkHistory.mockResolvedValue([]);
    mockedOrderApi.list.mockResolvedValue(emptyOrderPage);
  });

  it('取得に失敗しても一覧へ離脱せず、頁自身が失敗を名乗って再試行できること', async () => {
    mockedCustomerApi.get.mockRejectedValueOnce({ response: { status: 500 } });

    render(<CustomerEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('顧客情報の取得に失敗しました')).toBeInTheDocument();
    // 離脱すると説明責任が着地先へ移り、開いていた頁で再試行できなくなる
    expect(mockPush).not.toHaveBeenCalled();
    expect(notify.error).not.toHaveBeenCalled();

    mockedCustomerApi.get.mockResolvedValue(customer);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByDisplayValue('山田太郎')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('404 では再試行を出さず、一覧への導線だけを出すこと', async () => {
    mockedCustomerApi.get.mockRejectedValueOnce({ response: { status: 404 } });

    render(<CustomerEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('この顧客は見つかりませんでした')).toBeInTheDocument();
    // 何度押しても取れないものを押させない
    expect(within(region).queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    expect(within(region).getByRole('link', { name: '顧客一覧へ' })).toHaveAttribute(
      'href',
      '/store/1/customers'
    );
  });

  it('注文履歴だけが取れないときは、その区画だけが失敗を名乗り頁は編集できたままであること', async () => {
    mockedCustomerApi.get.mockResolvedValue(customer);
    mockedOrderApi.list.mockRejectedValueOnce(new Error('boom'));

    render(<CustomerEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('注文履歴の取得に失敗しました')).toBeInTheDocument();
    // 「注文履歴がありません」に化けると、来店の記録が無い顧客だと読める
    expect(screen.queryByText('注文履歴がありません')).not.toBeInTheDocument();
    // 頁全体が壊れたわけではない
    expect(screen.getByDisplayValue('山田太郎')).toBeInTheDocument();

    mockedOrderApi.list.mockResolvedValue(emptyOrderPage);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('注文履歴がありません')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
