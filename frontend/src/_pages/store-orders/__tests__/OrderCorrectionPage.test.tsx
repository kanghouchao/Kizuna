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

beforeEach(() => {
  jest.clearAllMocks();
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
