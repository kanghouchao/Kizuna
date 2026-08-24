import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import OrderCorrectionPage from '../ui/OrderCorrectionPage';
import { Order, orderApi } from '@/entities/order';
import { AxiosError } from 'axios';

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
    version: 7,
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
      // 開いた時点の版をそのまま返す。読み直さずに送ると、間に挟まった別の訂正を黙って巻き戻す
      expected_version: 7,
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

  it('会計金額の前後と、付与が動かないこと・どの会員の台帳を見るかを名乗ること', async () => {
    mockedOrderApi.get.mockResolvedValue(completedOrder());
    mockedOrderApi.correct.mockResolvedValue({
      previous_total_fee: 11900,
      total_fee: 17900,
    });
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額の誤記' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    // 差額を黙って出すだけだと「反映漏れ」に見える。動かない理由と行き先を同じ面に置く
    // 動いたのは会計金額だけ。差額も手当ての導線も出さない（門と手当てを結ぶ線が無いため）
    expect(await screen.findByText(/¥11,900 → ¥17,900/)).toBeInTheDocument();
    expect(screen.getByText(/この訂正では動きません/)).toBeInTheDocument();
    // どの会員の台帳を見ればよいかまでは名乗る。宛先は帰属記録が持つ会員で、顧客の現会員ではない
    expect(screen.getByText(/会員コード 123456789012/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /ポイントを調整/ })).not.toBeInTheDocument();
  });

  it('会員に帰属していない受注では差額も手当ての導線も出さないこと', async () => {
    // 帰属していない受注の付与 0pt は「少なく付いた付与」ではなく「付与が存在しない」。差額を出すと
    // 宛先の無い手動調整へ誘う
    mockedOrderApi.get.mockResolvedValue(completedOrder({ auto_grant_points: 0 }));
    mockedOrderApi.attribution.mockResolvedValue({ attributed: false });
    mockedOrderApi.correct.mockResolvedValue({
      previous_total_fee: 11900,
      total_fee: 17900,
    });
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額の誤記' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    expect(await screen.findByText(/動くポイントはありません/)).toBeInTheDocument();
    // 未申領の伝票は完了時点の会計で凍結した額を後から付与する。「動かない」と言い切らない
    expect(screen.getByText(/完了時点の会計に基づく額です/)).toBeInTheDocument();
    expect(screen.queryByText(/会員コード/)).not.toBeInTheDocument();
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
    });
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('実際の到着')).toHaveValue('19:35'));

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額の誤記' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    // 読めなければ帰属している側へ倒す（「動くポイントはありません」と誤って言い切らない）
    expect(await screen.findByText(/この訂正では動きません/)).toBeInTheDocument();
    expect(screen.getByText(/会員コード 不明/)).toBeInTheDocument();
  });

  it('版の食い違いでは取り直して最新の内容でフォームを組み直すこと', async () => {
    // 取り直さないと画面は古い版を持ったままで、その場の再送は何度でも 409 になる（死に筋）
    mockedOrderApi.get
      .mockResolvedValueOnce(completedOrder())
      .mockResolvedValue(completedOrder({ version: 9, course_name: '90 分コース' }));
    mockedOrderApi.correct.mockRejectedValue(
      new AxiosError('conflict', undefined, undefined, undefined, {
        status: 409,
        data: {},
        statusText: 'Conflict',
        headers: {},
        config: { headers: undefined as never },
      })
    );
    render(<OrderCorrectionPage />);
    await waitFor(() => expect(screen.getByLabelText('コース名')).toHaveValue('60 分コース'));

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額の誤記' } });
    fireEvent.click(screen.getByRole('button', { name: '訂正する' }));

    await waitFor(() => expect(notify.warning).toHaveBeenCalled());
    // 取り直した値で播き直る。入力は破棄され、頁は開いたまま
    await waitFor(() => expect(screen.getByLabelText('コース名')).toHaveValue('90 分コース'));
    expect(screen.getByRole('button', { name: '訂正する' })).toBeInTheDocument();
    expect(notify.error).not.toHaveBeenCalled();
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
