import { StrictMode } from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
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

  // Strict Mode は mount effect を二度走らせるので取得が二重に飛ぶ。失敗が顧客・注文履歴を
  // クリアする以上、遅れて着いた古い失敗が新しい成功を消してはいけない。
  // 二つの取得は独立に走るので、守衛も別々に確かめる
  it('二重 mount で古い失敗が後から着いても、顧客の取得結果を消さないこと', async () => {
    let failStale = (): void => {};
    mockedCustomerApi.get
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValue(customer);

    render(
      <StrictMode>
        <CustomerEditPage />
      </StrictMode>
    );

    expect(await screen.findByDisplayValue('山田太郎')).toBeInTheDocument();

    await act(async () => {
      failStale();
    });

    expect(screen.getByDisplayValue('山田太郎')).toBeInTheDocument();
    expect(screen.queryByText('顧客情報の取得に失敗しました')).not.toBeInTheDocument();
  });

  // 上の 1 本は成功・catch の比較しか固定しない（どちらの飛行も着いた後で観測するため、在途の
  // setIsLoading(false) は既に false の旗へ落ちる）。finally の比較は 2 度目を在途のまま留める
  // この形でしか赤にならない（注文履歴の取得は finally を持たないので、対象は顧客の取得だけ）
  it('二度目が在途のまま古い失敗が着いても、読み込み表示を畳まないこと', async () => {
    let failStale = (): void => {};
    mockedCustomerApi.get
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockReturnValueOnce(new Promise(() => {}));

    render(
      <StrictMode>
        <CustomerEditPage />
      </StrictMode>
    );

    await act(async () => {
      failStale();
    });

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });

  it('二重 mount で古い失敗が後から着いても、注文履歴の取得結果を消さないこと', async () => {
    mockedCustomerApi.get.mockResolvedValue(customer);
    let failStale = (): void => {};
    mockedOrderApi.list
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValue(emptyOrderPage);

    render(
      <StrictMode>
        <CustomerEditPage />
      </StrictMode>
    );

    expect(await screen.findByText('注文履歴がありません')).toBeInTheDocument();

    await act(async () => {
      failStale();
    });

    expect(screen.getByText('注文履歴がありません')).toBeInTheDocument();
    expect(screen.queryByText('注文履歴の取得に失敗しました')).not.toBeInTheDocument();
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
