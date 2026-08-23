import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import OrderCorrectionPage from '../ui/OrderCorrectionPage';
import { Order, orderApi } from '@/entities/order';

const mockPush = jest.fn();

jest.mock('@/entities/order', () => ({
  // 種別表などの定数は実物を通す。丸ごと差し替えると明細の欄が選択肢を組めない
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    get: jest.fn(),
    correct: jest.fn(),
    attribution: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => ({ storeId: '1', id: 'o1' }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

/** 完了した受注 1 件。ポイント利用の行は完了処理が書いた記録で、門内でも編集できない。 */
function completedOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 'o1',
    business_date: '2026-08-20',
    customer_id: 'c1',
    customer_name: '山田太郎',
    status: 'COMPLETED',
    course_name: '60 分コース',
    course_minutes: 60,
    actual_arrival_time: '19:35:00',
    total_fee: 11900,
    auto_grant_points: 120,
    fee_lines: [
      { kind: 'BASE_COURSE', name: '60 分コース', amount: 12000, system_owned: false },
      { kind: 'POINT_REDEMPTION', name: 'ポイント利用', amount: 100, system_owned: true },
    ],
    ...overrides,
  };
}

/** 完了時に会員へ帰属した受注の現況。差額の手当ての宛先はこの記録が持つ会員。 */
const ATTRIBUTED = { id: 1, attributed: true, member_code: '123456789012' };

beforeEach(() => {
  jest.clearAllMocks();
  mockedOrderApi.attribution.mockResolvedValue(ATTRIBUTED);
});

describe('完了後訂正のページ', () => {
  it('三組の全量を送り、送らなかった欄は空として運ぶこと', async () => {
    mockedOrderApi.get.mockResolvedValue(completedOrder());
    mockedOrderApi.correct.mockResolvedValue({
      previous_total_fee: 11900,
      total_fee: 17900,
      granted_points: 120,
      recomputed_grant_points: 180,
      grant_difference: 60,
    });
    render(<OrderCorrectionPage />);

    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));
    fireEvent.change(screen.getByLabelText('実際の終了'), { target: { value: '22:40' } });
    fireEvent.change(screen.getByLabelText('明細1の金額'), { target: { value: '18000' } });
    fireEvent.change(screen.getByLabelText('理由'), { target: { value: 'コースの取り違え' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    await waitFor(() => expect(mockedOrderApi.correct).toHaveBeenCalled());
    // 部分更新ではないので全量を毎回運ぶ。延長分数は空欄のまま＝「値なし」として送らない
    expect(mockedOrderApi.correct).toHaveBeenCalledWith('o1', {
      reason: 'コースの取り違え',
      actual_arrival_time: '19:35:00',
      actual_end_time: '22:40:00',
      course_name: '60 分コース',
      course_minutes: 60,
      extension_minutes: undefined,
      // ポイント利用の行は送らない（システム専有で、混ぜるとサーバが撥ねる）
      fee_lines: [{ kind: 'BASE_COURSE', name: undefined, amount: 18000 }],
    });
    expect(notify.success).toHaveBeenCalledWith('受注を訂正しました');
  });

  it('付与差額と手当ての行き先を提示し、自動で動かない理由を名乗ること', async () => {
    mockedOrderApi.get.mockResolvedValue(completedOrder());
    mockedOrderApi.correct.mockResolvedValue({
      previous_total_fee: 11900,
      total_fee: 17900,
      granted_points: 120,
      recomputed_grant_points: 180,
      grant_difference: 60,
    });
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額の誤記' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    // 差額を黙って出すだけだと「反映漏れ」に見える。動かない理由と行き先を同じ面に置く
    expect(await screen.findByText(/差 \+60pt/)).toBeInTheDocument();
    expect(screen.getByText(/自動で動きません/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ポイントを調整/ })).toHaveAttribute(
      'href',
      '/store/1/customers/c1/edit'
    );
  });

  it('会員に帰属していない受注では差額も手当ての導線も出さないこと', async () => {
    // 帰属していない受注の付与 0pt は「少なく付いた付与」ではなく「付与が存在しない」。差額を出すと
    // 宛先の無い手動調整へ誘う
    mockedOrderApi.get.mockResolvedValue(completedOrder({ auto_grant_points: 0 }));
    mockedOrderApi.attribution.mockResolvedValue({ attributed: false });
    mockedOrderApi.correct.mockResolvedValue({
      previous_total_fee: 11900,
      total_fee: 17900,
      granted_points: 0,
      recomputed_grant_points: 180,
      grant_difference: 180,
    });
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額の誤記' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    expect(await screen.findByText(/動くポイントはありません/)).toBeInTheDocument();
    expect(screen.queryByText(/差 \+180pt/)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /ポイントを調整/ })).not.toBeInTheDocument();
    // 訂正そのものは成立しているので、金額の変化は名乗る
    expect(screen.getByText(/¥11,900 → ¥17,900/)).toBeInTheDocument();
  });

  it('帰属の現況を読めなければ手当ての案内を出す側へ倒すこと', async () => {
    // 「動くポイントはありません」と誤って言い切ると、実際に残った誤付与に気づく機会が消える
    mockedOrderApi.get.mockResolvedValue(completedOrder());
    mockedOrderApi.attribution.mockRejectedValue(new Error('boom'));
    mockedOrderApi.correct.mockResolvedValue({
      previous_total_fee: 11900,
      total_fee: 17900,
      granted_points: 120,
      recomputed_grant_points: 180,
      grant_difference: 60,
    });
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額の誤記' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    expect(await screen.findByText(/差 \+60pt/)).toBeInTheDocument();
    expect(screen.getByText(/自動で動きません/)).toBeInTheDocument();
  });

  it('理由の無い訂正は送らないこと', async () => {
    mockedOrderApi.get.mockResolvedValue(completedOrder());
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));

    // 空白だけの理由も「書いていない」と同じ。確定した記録を動かす根拠がそこにしか残らない
    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    await waitFor(() =>
      expect(screen.getByText('訂正の理由を入力してください')).toBeInTheDocument()
    );
    expect(mockedOrderApi.correct).not.toHaveBeenCalled();
  });

  it('完了していない受注には欄を出さず、理由を名乗ること', async () => {
    // 欄を出してから 400 を返すと、入力し終えてから拒否を受け取ることになる
    mockedOrderApi.get.mockResolvedValue(completedOrder({ status: 'CANCELLED' }));
    render(<OrderCorrectionPage />);

    expect(await screen.findByText(/完了した受注だけが訂正できます/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '訂正する' })).not.toBeInTheDocument();
  });
});
