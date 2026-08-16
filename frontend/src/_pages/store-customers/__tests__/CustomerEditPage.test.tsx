import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
    memberLink: jest.fn(),
    memberLinkHistory: jest.fn(),
    memberPointBalance: jest.fn(),
  },
}));

jest.mock('@/entities/order', () => ({
  // 表示ラベル等の定数は本物を使う（API だけを差し替える）
  ...jest.requireActual('@/entities/order/model/types'),
  orderApi: { list: jest.fn() },
}));

const currentParams = { storeId: '1', id: 'cus-1' };

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => currentParams,
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedCustomerApi = customerApi as jest.Mocked<typeof customerApi>;
const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

const customer = {
  id: 'cus-1',
  name: '山田太郎',
  created_at: '2026-07-01T00:00:00Z',
  updated_at: '2026-07-01T00:00:00Z',
};

const emptyOrderPage = { rows: [], page: 0, pageCount: 1, total: 0 };

describe('顧客編集ページの取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    currentParams.id = 'cus-1';
    mockedCustomerApi.memberLink.mockRejectedValue({ response: { status: 404 } });
    mockedCustomerApi.memberLinkHistory.mockResolvedValue({ rows: [], nextCursor: null });
    mockedCustomerApi.memberPointBalance.mockResolvedValue({ linked: false });
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

  it('応答が空でも白紙にせず、再試行できる失敗として名乗ること', async () => {
    // 例外にならない空応答（204・本文なし）でも描くものは無い。白紙で返すと失敗の告知も
    // 再試行の導線も同時に消える。404 ではないので、出すのは再試行を持つ側の姿
    mockedCustomerApi.get.mockResolvedValueOnce(undefined as never);

    render(<CustomerEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('顧客情報の取得に失敗しました')).toBeInTheDocument();
    expect(within(region).getByRole('button', { name: '再試行' })).toBeInTheDocument();
    expect(screen.queryByText('この顧客は見つかりませんでした')).not.toBeInTheDocument();
    expect(mockPush).not.toHaveBeenCalled();
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

describe('顧客編集ページの注文履歴', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    currentParams.id = 'cus-1';
    mockedCustomerApi.get.mockResolvedValue(customer);
    mockedCustomerApi.memberLink.mockRejectedValue({ response: { status: 404 } });
    mockedCustomerApi.memberLinkHistory.mockResolvedValue({ rows: [], nextCursor: null });
  });

  it('受注ステータスを enum 生値ではなく受注一覧と同じ日本語ラベルで表示すること', async () => {
    mockedOrderApi.list.mockResolvedValue({
      rows: [{ id: 'ord-1', business_date: '2026-08-01', status: 'CONFIRMED', used_points: 0 }],
      page: 0,
      pageCount: 1,
      total: 1,
    });

    render(<CustomerEditPage />);

    expect(await screen.findByText('確定')).toBeInTheDocument();
    expect(screen.queryByText('CONFIRMED')).not.toBeInTheDocument();
  });
});

describe('顧客編集ページを統合済みの旧 ID で開いたとき', () => {
  const surviving = {
    ...customer,
    id: 'cus-2',
    name: '山田花子',
    merged: true,
    merged_from_id: 'cus-1',
  };

  beforeEach(() => {
    jest.clearAllMocks();
    currentParams.id = 'cus-1';
    mockedCustomerApi.get.mockResolvedValue(surviving);
    mockedCustomerApi.update.mockResolvedValue(surviving);
    mockedCustomerApi.memberLink.mockRejectedValue({ response: { status: 404 } });
    mockedCustomerApi.memberLinkHistory.mockResolvedValue({ rows: [], nextCursor: null });
    mockedCustomerApi.memberPointBalance.mockResolvedValue({ linked: false });
    mockedOrderApi.list.mockResolvedValue(emptyOrderPage);
  });

  it('附属区画と保存が、表示している存続行を相手にすること', async () => {
    // URL の旧 ID のまま続けると、表示は存続行なのに履歴と紐づけと保存だけが墓標を相手にする。
    // 墓標には受注も関連も残っていないので、空の区画と 409 が並ぶ画面になる
    render(<CustomerEditPage />);

    expect(await screen.findByDisplayValue('山田花子')).toBeInTheDocument();
    expect(mockedOrderApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ customer_id: 'cus-2' })
    );
    expect(mockedCustomerApi.memberLinkHistory).toHaveBeenLastCalledWith('cus-2', {
      cursor: undefined,
    });

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedCustomerApi.update).toHaveBeenCalledWith('cus-2', expect.anything())
    );
  });
});

describe('顧客編集ページの顧客切り替え', () => {
  const rowFor = (customerId: string) => ({
    rows: [
      {
        id: `link-${customerId}`,
        member_code: `CODE-${customerId}`,
        status: 'ACTIVE' as const,
        linked_at: '2026-08-01T10:00:00+09:00',
        linked_by_name: '山田次郎',
      },
    ],
    nextCursor: 'cursor-of-' + customerId,
  });

  beforeEach(() => {
    jest.clearAllMocks();
    currentParams.id = 'cus-1';
    mockedCustomerApi.get.mockResolvedValue(customer);
    mockedCustomerApi.memberLink.mockRejectedValue({ response: { status: 404 } });
    mockedCustomerApi.memberPointBalance.mockResolvedValue({ linked: false });
    mockedOrderApi.list.mockResolvedValue(emptyOrderPage);
    mockedCustomerApi.memberLinkHistory.mockImplementation((id: string) =>
      Promise.resolve(rowFor(id) as never)
    );
  });

  it('同じ画面位置で顧客が変わったら、前の顧客の紐づけ履歴を残さず取り直すこと', async () => {
    // App Router は [id] だけが変わる遷移で頁を再マウントしない。履歴の読み口は
    // マウント時にしか取りに行かないので、残った行は前の顧客のものとして描かれ続ける。
    const { rerender } = render(<CustomerEditPage />);
    expect(await screen.findByText('CODE-cus-1')).toBeInTheDocument();

    currentParams.id = 'cus-2';
    rerender(<CustomerEditPage />);

    expect(await screen.findByText('CODE-cus-2')).toBeInTheDocument();
    expect(screen.queryByText('CODE-cus-1')).not.toBeInTheDocument();
    expect(mockedCustomerApi.memberLinkHistory).toHaveBeenLastCalledWith('cus-2', {
      cursor: undefined,
    });
  });

  it('顧客が変わったら、入力途中の会員コードを持ち越さないこと', async () => {
    const { rerender } = render(<CustomerEditPage />);
    await screen.findByText('CODE-cus-1');

    fireEvent.change(screen.getByLabelText('会員コード'), { target: { value: '123456789012' } });
    expect(screen.getByLabelText('会員コード')).toHaveValue('123456789012');

    currentParams.id = 'cus-2';
    rerender(<CustomerEditPage />);

    await screen.findByText('CODE-cus-2');
    expect(screen.getByLabelText('会員コード')).toHaveValue('');
  });
});
