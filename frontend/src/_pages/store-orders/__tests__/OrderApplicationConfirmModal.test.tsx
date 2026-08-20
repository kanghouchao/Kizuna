import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { OrderApplicationConfirmModal } from '../ui/OrderApplicationConfirmModal';
import { OrderApplicationRow, orderApi, orderApplicationApi } from '@/entities/order';
import { customerApi } from '@/entities/customer';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order/model/types'),
  orderApi: { listReceptionists: jest.fn(), listCastCandidates: jest.fn() },
  orderApplicationApi: { confirm: jest.fn() },
}));

jest.mock('@/entities/customer', () => ({
  customerApi: { list: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedConfirm = orderApplicationApi.confirm as jest.Mock;
const mockedCustomerList = customerApi.list as jest.Mock;

function guestApplication(overrides: Partial<OrderApplicationRow> = {}): OrderApplicationRow {
  return {
    id: 'app-1',
    business_date: '2026-08-25',
    pax: 2,
    status: 'PENDING',
    expired: false,
    contact_name: 'ゲスト花子',
    contact_phone_number: '09000000000',
    ...overrides,
  };
}

function memberApplication(): OrderApplicationRow {
  return {
    id: 'app-2',
    business_date: '2026-08-25',
    pax: 2,
    status: 'PENDING',
    expired: false,
    requester_member_code: '123456789012',
    requester_declared_name: '名乗り太郎',
  };
}

const renderModal = (application: OrderApplicationRow | null) =>
  render(
    <OrderApplicationConfirmModal
      application={application}
      onClose={jest.fn()}
      onConfirmed={jest.fn()}
    />
  );

const confirmButton = () => screen.getByRole('button', { name: '確定する' });

/** キーボード/クリックで開く経路のみを使う（ポインタ系 API は jsdom に無い）。 */
async function pickCustomerMode(optionName: string) {
  fireEvent.click(await screen.findByRole('combobox', { name: /顧客/ }));
  const option = await screen.findByRole('option', { name: optionName });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

describe('OrderApplicationConfirmModal の顧客化', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (orderApi.listReceptionists as jest.Mock).mockResolvedValue([]);
    mockedConfirm.mockResolvedValue({ id: 'o1' });
  });

  it('ゲスト申請では顧客の決め方を店員に選ばせる', async () => {
    renderModal(guestApplication());

    expect(await screen.findByText('顧客（ゲスト申請）')).toBeInTheDocument();
  });

  it('会員申請では顧客を選ばせない（顧客は会員の紐づけが決める）', async () => {
    renderModal(memberApplication());

    expect(await screen.findByText('予約申請を確定')).toBeInTheDocument();
    expect(screen.queryByText('顧客（ゲスト申請）')).not.toBeInTheDocument();
  });

  it('新規作成を選ぶと申請の連絡先が予填されている', async () => {
    renderModal(guestApplication());
    await screen.findByText('顧客（ゲスト申請）');

    await pickCustomerMode('新規に台帳へ登録する');

    expect(await screen.findByLabelText('お客様名')).toHaveValue('ゲスト花子');
    expect(screen.getByLabelText('電話番号')).toHaveValue('09000000000');
  });

  it('既定は顧客未設定で、顧客の項目を送らずに確定する', async () => {
    renderModal(guestApplication());
    await screen.findByText('顧客（ゲスト申請）');

    fireEvent.click(confirmButton());

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    // 既定で台帳行を起こすと、店員が判断しないまま重複した行が積み上がる
    expect(mockedConfirm.mock.calls[0][1].customer_id).toBeUndefined();
    expect(mockedConfirm.mock.calls[0][1].new_customer).toBeUndefined();
  });

  it('新規作成を選んだ確定は new_customer を送る', async () => {
    renderModal(guestApplication());
    await screen.findByText('顧客（ゲスト申請）');
    await pickCustomerMode('新規に台帳へ登録する');
    await screen.findByLabelText('お客様名');

    fireEvent.click(confirmButton());

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].new_customer).toEqual({
      name: 'ゲスト花子',
      phone_number: '09000000000',
    });
  });

  it('既存顧客は店員が探して選んだ 1 行だけを送る（電話番号の自動照合はしない）', async () => {
    mockedCustomerList.mockResolvedValue({
      rows: [{ id: 'cust-1', name: 'ゲスト花子', phone_number: '09000000000' }],
      page: 0,
      pageCount: 1,
      total: 1,
    });
    renderModal(guestApplication());
    await screen.findByText('顧客（ゲスト申請）');
    await pickCustomerMode('既存の顧客を選ぶ');

    fireEvent.click(await screen.findByRole('button', { name: '検索' }));
    fireEvent.click(await screen.findByRole('button', { name: /ゲスト花子/ }));
    fireEvent.click(confirmButton());

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].customer_id).toBe('cust-1');
    expect(mockedConfirm.mock.calls[0][1].new_customer).toBeUndefined();
  });
});
