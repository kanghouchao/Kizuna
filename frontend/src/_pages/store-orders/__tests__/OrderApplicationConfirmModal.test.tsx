import { hasPermission } from '@/shared/lib';
import { chooseCourse, confirmPreview } from '../lib/orderTestSupport';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1' }) }));
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { OrderApplicationConfirmModal } from '../ui/OrderApplicationConfirmModal';
import { OrderApplicationRow, orderApi, orderApplicationApi } from '@/entities/order';
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  hasPermission: jest.fn(() => true),
}));

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    ...jest.requireActual('../lib/orderTestSupport').courseApiMocks(),
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
  },
  orderApplicationApi: {
    ...jest.requireActual('../lib/orderTestSupport').courseApiMocks(),
    confirm: jest.fn(),
    detail: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedConfirm = orderApplicationApi.confirm as jest.Mock;
const mockedCustomerList = orderApi.customerCandidates as jest.Mock;

function guestApplication(overrides: Partial<OrderApplicationRow> = {}): OrderApplicationRow {
  return {
    id: 'app-1',
    business_date: '2026-08-25',
    pax: 2,
    status: 'PENDING',
    expired: false,
    contact_snapshot: { name: 'ゲスト花子', phone_number: '09000000000' },
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
      open
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
    jest.mocked(hasPermission).mockReturnValue(true);
    (orderApi.listReceptionists as jest.Mock).mockResolvedValue([]);
    jest.mocked(orderApplicationApi.detail).mockResolvedValue({
      ...guestApplication(),
      business_contact_permissions: [],
      contact_imports: [],
    });
    mockedConfirm.mockResolvedValue({ id: 'o1' });
    jest
      .mocked(orderApi.specialServiceCandidates)
      .mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
  });

  it('申請が見つからない場合は保存と再試行を外し閉じると受付箱を更新する', async () => {
    jest.mocked(orderApplicationApi.detail).mockRejectedValueOnce({ response: { status: 404 } });
    const onMissing = jest.fn();
    render(
      <OrderApplicationConfirmModal
        open
        application={guestApplication()}
        onClose={jest.fn()}
        onConfirmed={jest.fn()}
        onMissing={onMissing}
      />
    );
    expect(
      await screen.findByText('予約申請が見つかりません。受付箱を更新してください。')
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '確定する' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
    expect(onMissing).toHaveBeenCalled();
  });

  it('新規顧客への連絡先と販促同意の取り込みをそれぞれ明示する', async () => {
    jest.mocked(orderApplicationApi.detail).mockResolvedValue({
      ...guestApplication(),
      contact_consent: {
        version: '1',
        business_text: '業務',
        marketing_text: '販促',
        business_allowed: true,
        marketing_allowed: true,
        acquired_at: '2026-09-25T10:00:00Z',
      },
      business_contact_permissions: [
        { type: 'PHONE', value: '+819000000000', status: 'ALLOWED', decision: 'ALLOWED' },
      ],
      contact_imports: [],
    });
    renderModal(guestApplication());
    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '取り込み先' },
    });
    fireEvent.click(await screen.findByRole('checkbox', { name: '電話を台帳へ取り込む' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '電話の販促同意を取り込む' }));
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();
    await waitFor(() =>
      expect(mockedConfirm).toHaveBeenCalledWith(
        'app-1',
        expect.objectContaining({
          contact_imports: [{ type: 'PHONE', import_marketing_consent: true }],
        })
      )
    );
  });

  it('申請の同意原文と今回の拒否判定を表示する', async () => {
    (orderApplicationApi as unknown as { detail: jest.Mock }).detail.mockResolvedValue({
      ...guestApplication(),
      contact_consent: {
        version: '1',
        business_text: '申請時の業務文面',
        marketing_text: '申請時の販促文面',
        business_allowed: true,
        marketing_allowed: false,
        acquired_at: '2026-09-25T10:00:00Z',
      },
      business_contact_permissions: [
        { type: 'PHONE', value: '+819000000000', status: 'ALLOWED', decision: 'STORE_DENIED' },
      ],
      contact_imports: [],
    });
    renderModal(guestApplication());
    expect(await screen.findByText('申請時の業務文面')).toBeInTheDocument();
    expect(screen.getByText(/店舗内の拒否により連絡不可/)).toBeInTheDocument();
    expect(screen.getByText(/販促同意：未選択/)).toBeInTheDocument();
  });

  it('空の新規顧客名は入力へ焦点を戻し、形式不正のメールは欄の傍で説明する', async () => {
    renderModal(guestApplication());
    await pickCustomerMode('新規顧客');
    await chooseCourse();
    fireEvent.click(confirmButton());
    const name = await screen.findByLabelText('新規顧客名');
    await waitFor(() => expect(name).toHaveFocus());
    expect(name).toHaveAttribute('aria-invalid', 'true');
    fireEvent.change(name, { target: { value: '新規顧客' } });
    fireEvent.change(screen.getByLabelText('メール'), { target: { value: 'invalid' } });
    fireEvent.click(confirmButton());
    await screen.findByText('メールアドレスの形式が正しくありません');
    expect(mockedConfirm).not.toHaveBeenCalled();
  });

  it('顧客管理権限がなくても既存選択と連絡先入力ができ、新規登録は表示しない', async () => {
    jest.mocked(hasPermission).mockReturnValue(false);
    renderModal(guestApplication());
    fireEvent.change(await screen.findByLabelText('メール'), {
      target: { value: 'once@example.com' },
    });
    fireEvent.click(await screen.findByRole('combobox', { name: '顧客の選択' }));
    expect(await screen.findByRole('option', { name: '既存顧客' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: '新規顧客' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('メール')).toHaveValue('once@example.com');
  });

  it('会員申請の空の連絡先からも名乗りを受注に写し、顧客選択は送らない', async () => {
    renderModal({
      ...memberApplication(),
      contact_snapshot: { name: null, phone_number: null, email: null, line_id: null },
    });
    expect(await screen.findByLabelText('お客様名')).toHaveValue('名乗り太郎');
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();
    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toBeUndefined();
    expect(mockedConfirm.mock.calls[0][1].contact_snapshot.name).toBe('名乗り太郎');
  });

  it.each([false, true])(
    '指名解除で特殊サービスを消し、解除を戻しても旧選択を送らない（戻す: %s）',
    async restore => {
      jest.mocked(orderApi.specialServiceCandidates).mockResolvedValue({
        rows: [
          {
            service_id: 'special-1',
            revision_id: 'sr1',
            revision_number: 1,
            terms_version: 1,
            name: '受諾済み追加',
            charge_type: 'PAID',
            price: 2000,
            remuneration: 1000,
            consent_event_id: 'consent-1',
            consent_version: 1,
          },
        ],
        page: 0,
        pageCount: 1,
        total: 1,
      });
      renderModal(guestApplication({ cast_id: 'cast-1', cast_name: '担当花子' }));
      await chooseCourse();
      const option = await screen.findByRole('checkbox', { name: /受諾済み追加/ });
      fireEvent.click(option);
      expect(option).toBeChecked();
      fireEvent.click(screen.getByRole('checkbox', { name: '指名を外して確定する' }));
      expect(screen.queryByRole('checkbox', { name: /受諾済み追加/ })).not.toBeInTheDocument();
      expect(
        screen.getByText('指名を外すため、特殊サービスは選択できません。')
      ).toBeInTheDocument();
      if (restore) {
        fireEvent.click(screen.getByRole('checkbox', { name: '指名を外して確定する' }));
        expect(await screen.findByRole('checkbox', { name: /受諾済み追加/ })).not.toBeChecked();
      }
      fireEvent.click(confirmButton());
      await confirmPreview();
      await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
      expect(jest.mocked(orderApplicationApi.previewConfirmation).mock.calls.at(-1)?.[1]).toEqual(
        expect.objectContaining({
          special_service_ids: [],
          cast_id: restore ? 'cast-1' : undefined,
        })
      );
      expect(mockedConfirm.mock.calls[0][1]).toEqual(
        expect.objectContaining({
          special_service_ids: [],
          cast_id: restore ? 'cast-1' : undefined,
        })
      );
    }
  );

  it('受付の明示選択は数値 ID で確定する', async () => {
    jest
      .mocked(orderApi.listReceptionists)
      .mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    renderModal(guestApplication());
    fireEvent.click(await screen.findByRole('combobox', { name: '受付担当' }));
    const item = await screen.findByRole('option', { name: '受付花子' });
    fireEvent.pointerDown(item);
    fireEvent.click(item);
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();
    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].receptionist_id).toBe(7);
  });

  it('ゲスト申請では顧客の決め方を店員に選ばせる', async () => {
    renderModal(guestApplication());

    expect(await screen.findByText('顧客の選択')).toBeInTheDocument();
  });

  it('会員申請では顧客を選ばせない（顧客は会員の紐づけが決める）', async () => {
    renderModal(memberApplication());

    expect(await screen.findByText('予約申請を確定')).toBeInTheDocument();
    expect(screen.queryByText('顧客の選択')).not.toBeInTheDocument();
  });

  it('新規作成を選ぶと申請の連絡先が予填されている', async () => {
    renderModal(guestApplication());
    await screen.findByText('顧客の選択');

    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '台帳の名前' },
    });

    expect(await screen.findByLabelText('お客様名')).toHaveValue('ゲスト花子');
    expect(screen.getByLabelText('電話番号')).toHaveValue('09000000000');
  });

  it('既定は顧客未設定で、顧客の項目を送らずに確定する', async () => {
    renderModal(guestApplication());
    await screen.findByText('顧客の選択');

    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    // 既定で台帳行を起こすと、店員が判断しないまま重複した行が積み上がる
    expect(mockedConfirm.mock.calls[0][1].receptionist_id).toBeUndefined();
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({ mode: 'NONE' });
    expect(mockedConfirm.mock.calls[0][1].new_customer).toBeUndefined();
  });

  it('新規作成を選んだ確定は new_customer を送る', async () => {
    renderModal(guestApplication());
    await screen.findByText('顧客の選択');
    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '台帳の名前' },
    });
    await screen.findByLabelText('お客様名');

    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({
      mode: 'NEW',
      new_customer: { name: '台帳の名前' },
    });
  });

  it('既存顧客は店員が探して選んだ 1 行だけを送る（電話番号の自動照合はしない）', async () => {
    mockedCustomerList.mockResolvedValue({
      rows: [{ id: 'cust-1', name: 'ゲスト花子', phone_number: '09000000000' }],
      nextCursor: null,
    });
    renderModal(guestApplication());
    await screen.findByText('顧客の選択');
    await pickCustomerMode('既存顧客');

    fireEvent.click(await screen.findByRole('combobox', { name: '既存顧客の検索' }));
    const candidate = await screen.findByRole('option', { name: /ゲスト花子/ });
    fireEvent.pointerDown(candidate);
    fireEvent.click(candidate);
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({
      mode: 'EXISTING',
      customer_id: 'cust-1',
    });
    expect(mockedConfirm.mock.calls[0][1].new_customer).toBeUndefined();
  });

  it('ゲスト申請の確定でも、顧客の決め方と一緒に選択したコースを運ぶ', async () => {
    // 確定は受注の出生なので、快照が写る経路は会員申請とゲスト申請で分かれない。
    // 顧客の決め方（ゲスト専用）と快照（両方）が同じ要求に同居することを固定する
    renderModal(guestApplication());
    await screen.findByText('顧客の選択');
    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '台帳の名前' },
    });
    await screen.findByLabelText('お客様名');

    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].course_id).toBe('course-1');
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({
      mode: 'NEW',
      new_customer: { name: '台帳の名前' },
    });
  });
});

describe('確定対象と受付候補の取得', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(hasPermission).mockReturnValue(true);
  });

  it.each(['同じ申請を開き直す', '開いたまま別申請へ切り替える'])(
    '%s と最新の受付候補を表示する',
    async mode => {
      const list = jest.mocked(orderApi.listReceptionists);
      list.mockResolvedValueOnce([{ id: 7, display_name: '旧受付' }]);
      const props = { onClose: jest.fn(), onConfirmed: jest.fn() };
      const application = guestApplication();
      const { rerender } = render(
        <OrderApplicationConfirmModal open {...props} application={null} />
      );
      expect(list).not.toHaveBeenCalled();
      rerender(<OrderApplicationConfirmModal open {...props} application={application} />);
      fireEvent.click(await screen.findByRole('combobox', { name: '受付担当' }));
      const old = await screen.findByRole('option', { name: '旧受付' });
      fireEvent.pointerDown(old);
      fireEvent.click(old);

      list.mockResolvedValueOnce([{ id: 9, display_name: '新受付' }]);
      if (mode === '同じ申請を開き直す') {
        rerender(<OrderApplicationConfirmModal open {...props} application={null} />);
        expect(list).toHaveBeenCalledTimes(1);
      }
      rerender(
        <OrderApplicationConfirmModal
          open
          {...props}
          application={
            mode === '同じ申請を開き直す' ? application : guestApplication({ id: 'app-3' })
          }
        />
      );
      fireEvent.click(await screen.findByRole('combobox', { name: '受付担当' }));
      expect(await screen.findByRole('option', { name: '新受付' })).toBeInTheDocument();
      expect(screen.queryByRole('option', { name: '旧受付' })).not.toBeInTheDocument();
    }
  );
});
