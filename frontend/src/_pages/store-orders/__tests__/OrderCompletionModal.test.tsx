import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { OrderCompletionModal } from '../ui/OrderCompletionModal';
import { Order, orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  // 種別表などの定数は実物を通す。丸ごと差し替えると明細の欄が選択肢を組めない
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    get: jest.fn(),
    complete: jest.fn(),
    completionPreview: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

// QR は描画された画像からは中身を読めない。運ぶ値そのものを断言できるよう、値を属性へ出す差し替えにする
jest.mock('qrcode.react', () => ({
  QRCodeSVG: ({ value, ...props }: { value: string; 'aria-label': string; role: string }) => (
    <span data-testid="qr" data-value={value} aria-label={props['aria-label']} role={props.role} />
  ),
}));

const mockedGet = orderApi.get as jest.Mock;
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

/** 会計の内訳を 1 行だけ入れて完了する（各テストの本題は入力側なので、送信までを 1 つにまとめる）。 */
const completeWith = async (amount: string) => {
  fireEvent.change(await screen.findByLabelText('明細1の名称'), { target: { value: '会計' } });
  fireEvent.change(screen.getByLabelText('明細1の金額'), { target: { value: amount } });
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
    // 完了モーダルは作業キューの行から開かれる。行は明細もコース名も持たないので、
    // 播種は詳細の読み口から取り直す（既存の内訳を空で上書きしないため）。
    mockedGet.mockImplementation(async (id: string) => ({ ...confirmedOrder, id }));
  });

  it('作業キューの行から開いても、既存の内訳を詳細の読み口から播き直す', async () => {
    // 行は fee_lines も course_name も持たない。行だけで播くと、明細のある受注が空行で開き、
    // そのまま完了すると既存の内訳を丸ごと上書きして失う
    mockedGet.mockResolvedValue({
      ...confirmedOrder,
      course_name: '90 分コース',
      fee_lines: [
        { kind: 'BASE_COURSE', name: '90 分コース', amount: 18000, system_owned: false },
        { kind: 'OPTION', name: '指名', amount: 2000, system_owned: false },
      ],
    });
    const queueRow: Order = { id: 'o1', status: 'CONFIRMED', customer_name: '山田太郎' };

    render(<OrderCompletionModal order={queueRow} onClose={jest.fn()} onCompleted={jest.fn()} />);

    expect(await screen.findByLabelText('コース名')).toHaveValue('90 分コース');
    expect(screen.getByLabelText('明細2の名称')).toHaveValue('指名');
    expect(mockedGet).toHaveBeenCalledWith('o1');
  });

  it('播いた内訳の総和で初回の見込みを取る', async () => {
    // committed の初期値を 0 のままにすると、明細のある受注の付与予定が最初の 1 画面だけ 0 で出る
    mockedGet.mockResolvedValue({
      ...confirmedOrder,
      fee_lines: [{ kind: 'OPTION', name: '指名', amount: 12000, system_owned: false }],
    });

    renderModal();

    await waitFor(() => expect(mockedPreview).toHaveBeenCalledWith('o1', 12000));
    expect(mockedPreview).not.toHaveBeenCalledWith('o1', 0);
  });

  it('コース名を空にしたら、省略ではなく空文字で送って消せる', async () => {
    // undefined はキーごと落ちてサーバが「変更しない」と読むため、消したい意図が黙って捨てられる
    mockedGet.mockResolvedValue({ ...confirmedOrder, course_name: '90 分コース' });
    renderModal();

    fireEvent.change(await screen.findByLabelText('コース名'), { target: { value: '' } });
    await completeWith('8000');

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][1].course_name).toBe('');
  });

  it('開き直しは陳腐化した内訳で播かず、取り直した内容で播く', async () => {
    // useResource は取り直しの間も前の値を持ったまま。その値で播いて印を立てると、後から着いた
    // 新しい内容が捨てられ、他の操作者が直したばかりの明細を古い内訳で上書きして完了できてしまう
    mockedGet
      .mockResolvedValueOnce({
        ...confirmedOrder,
        fee_lines: [{ kind: 'OPTION', name: '古い明細', amount: 1000, system_owned: false }],
      })
      .mockResolvedValueOnce({
        ...confirmedOrder,
        fee_lines: [{ kind: 'OPTION', name: '新しい明細', amount: 5000, system_owned: false }],
      });
    const { rerender } = renderModal();
    expect(await screen.findByDisplayValue('古い明細')).toBeInTheDocument();

    rerender(<OrderCompletionModal order={null} onClose={jest.fn()} onCompleted={jest.fn()} />);
    rerender(
      <OrderCompletionModal order={confirmedOrder} onClose={jest.fn()} onCompleted={jest.fn()} />
    );

    expect(await screen.findByDisplayValue('新しい明細')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('古い明細')).not.toBeInTheDocument();
  });

  it('受注を取得できなければ、その領域が失敗を名乗り再試行を出す', async () => {
    // 失敗を捨てると「読み込み中」のまま固まり、閉じる以外に何も起こせない画面になる
    mockedGet.mockRejectedValueOnce(new Error('boom'));
    renderModal();

    expect(await screen.findByText('受注を取得できませんでした。')).toBeInTheDocument();
    expect(screen.queryByLabelText('明細1の金額')).not.toBeInTheDocument();

    mockedGet.mockResolvedValueOnce({
      ...confirmedOrder,
      fee_lines: [{ kind: 'OPTION', name: '再取得できた明細', amount: 3000, system_owned: false }],
    });
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByDisplayValue('再取得できた明細')).toBeInTheDocument();
  });

  it('閉じている間は事前計算を取りに行かない', () => {
    // 一覧に常時 mount されているので、開くまで取りに行くと 1 件も完了しない画面が毎回読む
    render(<OrderCompletionModal order={null} onClose={jest.fn()} onCompleted={jest.fn()} />);

    expect(mockedPreview).not.toHaveBeenCalled();
  });

  it('顧客未設定の受注は録入された連絡先で見出しに出す', async () => {
    // 会計の相手が誰か分からないまま完了させないため、台帳に着かなかった受注でも呼び名を出す
    const unlinked: Order = {
      ...confirmedOrder,
      customer_name: undefined,
      contact_name: '重複照合の来客',
    };
    render(<OrderCompletionModal order={unlinked} onClose={jest.fn()} onCompleted={jest.fn()} />);

    expect(await screen.findByText(/重複照合の来客（顧客未設定）/)).toBeInTheDocument();
  });

  it('開いた時点の事前計算は会計金額 0 で取る', async () => {
    renderModal();

    await waitFor(() => expect(mockedPreview).toHaveBeenCalledWith('o1', 0));
  });

  it('会計金額を確定すると、その金額で見込みを取り直す', async () => {
    // 打鍵ごとに取りに行かないので、欄を離れるまでは前の金額の見込みのまま
    renderModal();
    await screen.findByLabelText('明細1の金額');

    const input = screen.getByLabelText('明細1の金額');
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
    // 見込みは播種の後に走る。播く前に引くと、内訳の入る前の総和で付与予定が嘘になる
    await waitFor(() => expect(mockedPreview).toHaveBeenLastCalledWith('o2', 0));
    expect(screen.queryByLabelText('利用ポイント')).not.toBeInTheDocument();
  });

  it('同じ受注を開き直したら、前回確定した会計金額で見込みを取り直さない', async () => {
    // 欄は空に戻るので、確定値だけ残ると空欄のまま前回の金額の付与予定が出る
    const { rerender } = render(
      <OrderCompletionModal order={confirmedOrder} onClose={jest.fn()} onCompleted={jest.fn()} />
    );
    const input = await screen.findByLabelText('明細1の金額');
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

    expect(await screen.findByText('金額を入力してください')).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('会計金額が小数なら送信せず理由を出す', async () => {
    // noValidate は type="number" の暗黙の step=1 まで止める
    renderModal();

    await completeWith('1500.5');

    expect(await screen.findByText('金額は整数で入力してください')).toBeInTheDocument();
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

  it('利用ポイントが会計金額を超えたら送信せず理由を出す', async () => {
    // 請求より大きい割引に相当する利用を台帳へ積ませない。残高では引っ掛からない額で確かめる
    mockedPreview.mockResolvedValue({
      member_linked: true,
      point_balance: 5000,
      usage_unit: 100,
      grant_points: 10,
    });
    renderModal();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '2000' } });
    await completeWith('1000');

    expect(await screen.findByText('会計金額を超えています（会計金額: 1000）')).toBeInTheDocument();
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
    expect(mockedComplete.mock.calls[0][1].fee_lines).toEqual([
      { kind: 'OPTION', name: '会計', amount: 8000 },
    ]);
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
    expect(mockedComplete.mock.calls[0][1]).toEqual({
      course_name: '',
      fee_lines: [{ kind: 'OPTION', name: '会計', amount: 8000 }],
      use_points: 200,
    });
  });

  it('会員でなくなった見込みへ、打ち込み済みの利用ポイントを持ち越さない', async () => {
    // 欄が消えても react-hook-form は値を保つ。入力だけを見て送ると、紐づけが読めなくなった
    // 受注へ利用が漏れる
    renderModal();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    mockedPreview.mockResolvedValue({ member_linked: false, usage_unit: 100, grant_points: 80 });
    fireEvent.change(screen.getByLabelText('明細1の名称'), { target: { value: '会計' } });
    const input = screen.getByLabelText('明細1の金額');
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

  it('伝票トークンが返ったら閉じずに QR を出す', async () => {
    // 生値は完了応答にしか現れない。ここで閉じると、客が後から来店を取り戻す手段ごと消える
    const onClose = jest.fn();
    const onCompleted = jest.fn();
    mockedPreview.mockResolvedValue({ member_linked: false, usage_unit: 100, grant_points: 50 });
    mockedComplete.mockResolvedValue({ ...confirmedOrder, receipt_token: 'raw-receipt-token' });
    renderModal(onCompleted, onClose);

    await completeWith('8000');

    expect(await screen.findByLabelText('伝票QR')).toBeInTheDocument();
    // 客が読み取って行き着くのは申領画面。トークンだけを載せると、読み取っても開く先が無い
    expect(screen.getByTestId('qr')).toHaveAttribute(
      'data-value',
      'http://kizuna.test/member/receipts#raw-receipt-token'
    );
    expect(onCompleted).toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
    // 会計の欄は役目を終えている。QR と入れ替える
    expect(screen.queryByLabelText('明細1の金額')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('QR を出している間は Escape で閉じない', async () => {
    // 生値はこの応答にしか無い。誤って閉じると客が来店を取り戻す手段ごと消えるので、
    // 閉じるのは明示のボタンだけにする
    const onClose = jest.fn();
    mockedPreview.mockResolvedValue({ member_linked: false, usage_unit: 100, grant_points: 50 });
    mockedComplete.mockResolvedValue({ ...confirmedOrder, receipt_token: 'raw-receipt-token' });
    renderModal(jest.fn(), onClose);

    await completeWith('8000');
    await screen.findByLabelText('伝票QR');
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByLabelText('伝票QR')).toBeInTheDocument();
  });

  it('会計の入力中は Escape で閉じる', async () => {
    // 閉じない扱いは QR を出している間だけ。入力中まで塞ぐと、開いただけのモーダルから出られない
    const onClose = jest.fn();
    renderModal(jest.fn(), onClose);
    await screen.findByLabelText('明細1の金額');

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalled();
  });

  it('伝票トークンが返らない完了では QR を出さずに閉じる', async () => {
    // 会員へ帰属した完了に事後帰属の余地は無い
    const onClose = jest.fn();
    renderModal(jest.fn(), onClose);

    await completeWith('8000');

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(screen.queryByLabelText('伝票QR')).not.toBeInTheDocument();
  });

  it('別の受注へ切り替えたら、前の受注の QR を持ち越さない', async () => {
    // 発行済みのトークンは受注 1 件に結びついている。持ち越すと、別の来店の QR を客に読ませる
    const otherOrder: Order = { ...confirmedOrder, id: 'o2', customer_name: '鈴木花子' };

    await reopenAfterIssuing(otherOrder);

    expect(await screen.findByLabelText('明細1の金額')).toBeInTheDocument();
    expect(screen.queryByLabelText('伝票QR')).not.toBeInTheDocument();
  });

  it('同じ受注を開き直しても、前に発行した QR は出さない', async () => {
    // 生値は発行の応答にしか現れない。閉じた後に出し直せると、「今だけ」と書いた画面が嘘になる
    await reopenAfterIssuing({ ...confirmedOrder });

    expect(await screen.findByLabelText('明細1の金額')).toBeInTheDocument();
    expect(screen.queryByLabelText('伝票QR')).not.toBeInTheDocument();
  });

  /** 受注 o1 を完了して QR を出し、いったん閉じてから指定の受注で開き直す。 */
  const reopenAfterIssuing = async (reopened: Order) => {
    mockedPreview.mockResolvedValue({ member_linked: false, usage_unit: 100, grant_points: 50 });
    mockedComplete.mockResolvedValue({ ...confirmedOrder, receipt_token: 'raw-receipt-token' });
    const { rerender } = render(
      <OrderCompletionModal order={confirmedOrder} onClose={jest.fn()} onCompleted={jest.fn()} />
    );
    await completeWith('8000');
    await screen.findByLabelText('伝票QR');

    rerender(<OrderCompletionModal order={null} onClose={jest.fn()} onCompleted={jest.fn()} />);
    rerender(<OrderCompletionModal order={reopened} onClose={jest.fn()} onCompleted={jest.fn()} />);
  };

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
