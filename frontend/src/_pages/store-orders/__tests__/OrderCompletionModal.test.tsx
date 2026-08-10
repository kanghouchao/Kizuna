import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { OrderCompletionModal } from '../ui/OrderCompletionModal';
import { Order, orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    complete: jest.fn(),
    completionPreview: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedComplete = orderApi.complete as jest.Mock;
const mockedPreview = orderApi.completionPreview as jest.Mock;

const confirmedOrder: Order = {
  id: 'o1',
  status: 'CONFIRMED',
  business_date: '2026-08-10',
  customer_name: '山田太郎',
};

const renderModal = (onCompleted = jest.fn(), onClose = jest.fn()) =>
  render(
    <OrderCompletionModal order={confirmedOrder} onClose={onClose} onCompleted={onCompleted} />
  );

/** 会計金額を入れて完了する（各テストの本題は入力側なので、送信までを 1 つにまとめる）。 */
const completeWith = async (totalFee: string) => {
  fireEvent.change(await screen.findByLabelText('会計金額'), { target: { value: totalFee } });
  fireEvent.click(screen.getByRole('button', { name: '完了する' }));
};

describe('OrderCompletionModal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedPreview.mockResolvedValue({
      member_linked: true,
      point_balance: 500,
      usage_unit: 100,
      grant_points: 50,
    });
    mockedComplete.mockResolvedValue(confirmedOrder);
  });

  it('閉じている間は事前計算を取りに行かない', () => {
    // 一覧に常時 mount されているので、開くまで取りに行くと 1 件も完了しない画面が毎回読む
    render(<OrderCompletionModal order={null} onClose={jest.fn()} onCompleted={jest.fn()} />);

    expect(mockedPreview).not.toHaveBeenCalled();
  });

  it('開いた時点の事前計算は会計金額 0 で取る', async () => {
    renderModal();

    await waitFor(() => expect(mockedPreview).toHaveBeenCalledWith('o1', 0));
  });

  it('会計金額を確定すると、その金額で見込みを取り直す', async () => {
    // 打鍵ごとに取りに行かないので、欄を離れるまでは前の金額の見込みのまま
    renderModal();
    await screen.findByLabelText('会計金額');

    const input = screen.getByLabelText('会計金額');
    fireEvent.change(input, { target: { value: '8000' } });
    fireEvent.blur(input);

    await waitFor(() => expect(mockedPreview).toHaveBeenLastCalledWith('o1', 8000));
  });

  it('別の受注へ切り替えたら、前の受注の見込みで欄の可否を決めない', async () => {
    // 取得フックは取り直しの間も前の値を保つ。値だけを見ると、会員だった前の受注の規則で
    // 次の受注の利用ポイントを受け付けてしまう
    const otherOrder: Order = { ...confirmedOrder, id: 'o2', customer_name: '鈴木花子' };
    const { rerender } = render(
      <OrderCompletionModal order={confirmedOrder} onClose={jest.fn()} onCompleted={jest.fn()} />
    );
    await screen.findByLabelText('利用ポイント');

    rerender(<OrderCompletionModal order={null} onClose={jest.fn()} onCompleted={jest.fn()} />);
    mockedPreview.mockReturnValue(new Promise(() => {}));
    rerender(
      <OrderCompletionModal order={otherOrder} onClose={jest.fn()} onCompleted={jest.fn()} />
    );

    expect(await screen.findByText('読み込み中...')).toBeInTheDocument();
    expect(mockedPreview).toHaveBeenLastCalledWith('o2', 0);
    expect(screen.queryByLabelText('利用ポイント')).not.toBeInTheDocument();
  });

  it('同じ受注を開き直したら、前回確定した会計金額で見込みを取り直さない', async () => {
    // 欄は空に戻るので、確定値だけ残ると空欄のまま前回の金額の付与予定が出る
    const { rerender } = render(
      <OrderCompletionModal order={confirmedOrder} onClose={jest.fn()} onCompleted={jest.fn()} />
    );
    const input = await screen.findByLabelText('会計金額');
    fireEvent.change(input, { target: { value: '8000' } });
    fireEvent.blur(input);
    await waitFor(() => expect(mockedPreview).toHaveBeenLastCalledWith('o1', 8000));

    rerender(<OrderCompletionModal order={null} onClose={jest.fn()} onCompleted={jest.fn()} />);
    rerender(
      <OrderCompletionModal order={confirmedOrder} onClose={jest.fn()} onCompleted={jest.fn()} />
    );

    await waitFor(() => expect(mockedPreview).toHaveBeenLastCalledWith('o1', 0));
  });

  it('未紐づけの受注では利用ポイント欄を出さず、未紐づけと名乗る', async () => {
    // 非会員に台帳は存在しない。欄を出すと、必ず失敗する入力を勧めることになる
    mockedPreview.mockResolvedValue({ member_linked: false, usage_unit: 100, grant_points: 50 });
    renderModal();

    expect(await screen.findByText('未紐づけ')).toBeInTheDocument();
    expect(screen.queryByLabelText('利用ポイント')).not.toBeInTheDocument();
    expect(screen.queryByText(/残高:/)).not.toBeInTheDocument();
    // 非会員には付与もされない。予定だけ出すと、完了しても増えないポイントを約束することになる
    expect(screen.queryByText(/付与予定:/)).not.toBeInTheDocument();
  });

  it('紐づけ済みの受注では残高と付与予定を出し、利用ポイントを受け付ける', async () => {
    renderModal();

    expect(await screen.findByText('会員紐づけ済み')).toBeInTheDocument();
    expect(screen.getByText('残高: 500 ポイント')).toBeInTheDocument();
    expect(screen.getByText('付与予定: 50 ポイント')).toBeInTheDocument();
    expect(screen.getByText('利用は 100 ポイント単位で指定できます')).toBeInTheDocument();
    expect(screen.getByLabelText('利用ポイント')).toBeInTheDocument();
  });

  it('会計金額が空欄なら送信せず、無反応にもせず理由を出す', async () => {
    // 空欄は NaN であって null でも空文字でもないため、required だけでは素通りする
    renderModal();

    fireEvent.click(await screen.findByRole('button', { name: '完了する' }));

    expect(await screen.findByText('会計金額を入力してください')).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('会計金額が小数なら送信せず理由を出す', async () => {
    // noValidate は type="number" の暗黙の step=1 まで止める
    renderModal();

    await completeWith('1500.5');

    expect(await screen.findByText('会計金額は整数で入力してください')).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('利用ポイントが単位に合わなければ送信せず理由を出す', async () => {
    renderModal();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '150' } });
    await completeWith('8000');

    expect(
      await screen.findByText('利用ポイントは 100 ポイント単位で指定してください')
    ).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('利用ポイントが残高を超えたら送信せず理由を出す', async () => {
    renderModal();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '600' } });
    await completeWith('8000');

    expect(await screen.findByText('残高を超えています（残高: 500）')).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('利用ポイントが空欄なら、利用の項目ごと送らない', async () => {
    // サーバ側は @Min(1)。0 を送ると撥ねられるので、利用しない完了ではキーごと落とす
    const onCompleted = jest.fn();
    const onClose = jest.fn();
    renderModal(onCompleted, onClose);
    await screen.findByLabelText('利用ポイント');

    await completeWith('8000');

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][0]).toBe('o1');
    expect(mockedComplete.mock.calls[0][1].total_fee).toBe(8000);
    expect(mockedComplete.mock.calls[0][1].use_points).toBeUndefined();
    expect(notify.success).toHaveBeenCalledWith('オーダーを完了しました');
    expect(onCompleted).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('利用ポイントを入れたら、その値を添えて送る', async () => {
    renderModal();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    await completeWith('8000');

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][1]).toEqual({ total_fee: 8000, use_points: 200 });
  });

  it('会員でなくなった見込みへ、打ち込み済みの利用ポイントを持ち越さない', async () => {
    // 欄が消えても react-hook-form は値を保つ。入力だけを見て送ると、紐づけが読めなくなった
    // 受注へ利用が漏れる
    renderModal();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    mockedPreview.mockResolvedValue({ member_linked: false, usage_unit: 100, grant_points: 80 });
    const input = screen.getByLabelText('会計金額');
    fireEvent.change(input, { target: { value: '8000' } });
    fireEvent.blur(input);

    await screen.findByText('未紐づけ');
    expect(screen.queryByLabelText('利用ポイント')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][1].use_points).toBeUndefined();
  });

  it('完了に失敗したら、対処方法を含むサーバの文言をそのまま出す', async () => {
    // 残高不足・単位違反は行動できる文言で返ってくる。汎用文言に潰さない
    mockedComplete.mockRejectedValue({
      response: { status: 400, data: { error: 'ポイント残高が不足しています（残高: 500）' } },
    });
    const onCompleted = jest.fn();
    renderModal(onCompleted);

    await completeWith('8000');

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith('ポイント残高が不足しています（残高: 500）')
    );
    expect(onCompleted).not.toHaveBeenCalled();
  });

  it('事前計算に失敗したら領域が自分で名乗り、通知には出さず、送信も塞がない', async () => {
    // 見込みが読めないことは入力の誤りではない。単位も残高もサーバ側が再検証するので、
    // ここで送信を塞ぐと読み込みの失敗が会計そのものを止めてしまう
    mockedPreview.mockRejectedValue(new Error('boom'));
    renderModal();

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('ポイントの見込みを取得できませんでした')).toBeInTheDocument();
    expect(notify.error).not.toHaveBeenCalled();
    // 会員かどうかが分からない以上、利用ポイントは受け付けない
    expect(screen.queryByLabelText('利用ポイント')).not.toBeInTheDocument();

    await completeWith('8000');

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][1].use_points).toBeUndefined();
  });

  it('見込みの再試行は同じ会計金額のまま取り直す', async () => {
    mockedPreview.mockRejectedValueOnce(new Error('boom'));
    renderModal();

    const region = await screen.findByRole('alert');
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('会員紐づけ済み')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('送信中はキャンセルも完了もどちらも押せない', async () => {
    // 台帳へ記帳されたか分からないまま閉じられると、一覧が古いまま残る
    let settle: (order: Order) => void = () => {};
    mockedComplete.mockReturnValueOnce(
      new Promise(resolve => {
        settle = resolve;
      })
    );
    renderModal();

    await completeWith('8000');

    await waitFor(() => expect(screen.getByRole('button', { name: '処理中...' })).toBeDisabled());
    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeDisabled();

    await act(async () => settle(confirmedOrder));
  });
});
