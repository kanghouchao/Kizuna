import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { PointAdjustmentDialog } from '../ui/PointAdjustmentDialog';
import { customerApi } from '@/entities/customer';

jest.mock('@/entities/customer', () => ({
  customerApi: { adjustPoints: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedAdjust = customerApi.adjustPoints as jest.Mock;

const renderDialog = (onAdjusted = jest.fn(), onClose = jest.fn()) => {
  render(<PointAdjustmentDialog customerId="c1" open onClose={onClose} onAdjusted={onAdjusted} />);
  return { onAdjusted, onClose };
};

/** 増減と事由を入れて送信する（各テストの本題は個々の欄なので、送信までを 1 つにまとめる）。 */
const submitWith = async (delta: string, reason = '棚卸しの補正') => {
  fireEvent.change(await screen.findByLabelText('増減ポイント'), { target: { value: delta } });
  fireEvent.change(screen.getByLabelText('事由'), { target: { value: reason } });
  fireEvent.click(screen.getByRole('button', { name: '調整する' }));
};

describe('PointAdjustmentDialog', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAdjust.mockResolvedValue({ linked: true, balance: 220 });
  });

  it('増減ポイントが空欄なら送信せず、無反応にもせず理由を出す', async () => {
    // 空欄は NaN であって null でも空文字でもないため、required だけでは素通りする
    renderDialog();

    fireEvent.change(await screen.findByLabelText('事由'), { target: { value: '補正' } });
    fireEvent.click(screen.getByRole('button', { name: '調整する' }));

    expect(await screen.findByText('増減ポイントを入力してください')).toBeInTheDocument();
    expect(mockedAdjust).not.toHaveBeenCalled();
  });

  it('事由が空欄なら送信せず理由を出す', async () => {
    // 誰が何のために動かしたか辿れない仕訳を台帳へ積ませない
    renderDialog();

    fireEvent.change(await screen.findByLabelText('増減ポイント'), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: '調整する' }));

    expect(await screen.findByText('事由を入力してください')).toBeInTheDocument();
    expect(mockedAdjust).not.toHaveBeenCalled();
  });

  it('増減 0 は送信せず理由を出す', async () => {
    // 台帳が撥ねる値を往復させない
    renderDialog();

    await submitWith('0');

    expect(await screen.findByText('0 以外の増減を入力してください')).toBeInTheDocument();
    expect(mockedAdjust).not.toHaveBeenCalled();
  });

  it('増減が小数なら送信せず理由を出す', async () => {
    // noValidate は type="number" の暗黙の step=1 まで止める
    renderDialog();

    await submitWith('1.5');

    expect(await screen.findByText('増減ポイントは整数で入力してください')).toBeInTheDocument();
    expect(mockedAdjust).not.toHaveBeenCalled();
  });

  it('加算では有効期限を受け付け、入力した日付を添えて送る', async () => {
    renderDialog();

    fireEvent.change(await screen.findByLabelText('増減ポイント'), { target: { value: '100' } });
    fireEvent.change(await screen.findByLabelText('有効期限'), {
      target: { value: '2026-12-31' },
    });
    fireEvent.change(screen.getByLabelText('事由'), { target: { value: 'キャンペーン付与' } });
    fireEvent.click(screen.getByRole('button', { name: '調整する' }));

    await waitFor(() => expect(mockedAdjust).toHaveBeenCalledTimes(1));
    expect(mockedAdjust.mock.calls[0][0]).toBe('c1');
    expect(mockedAdjust.mock.calls[0][1]).toEqual({
      delta: 100,
      reason: 'キャンペーン付与',
      expires_on: '2026-12-31',
      idempotency_key: expect.any(String),
    });
    expect(mockedAdjust.mock.calls[0][1].idempotency_key).not.toBe('');
  });

  it('有効期限が空欄なら、その項目ごと送らない', async () => {
    renderDialog();

    await submitWith('100');

    await waitFor(() => expect(mockedAdjust).toHaveBeenCalledTimes(1));
    expect(mockedAdjust.mock.calls[0][1].expires_on).toBeUndefined();
  });

  it('減算へ切り替えたら有効期限の欄を消し、打ち込み済みの日付も送らない', async () => {
    // 欄が消えても react-hook-form は値を保つ。減算に期限を添えるとサーバが撥ねる
    renderDialog();

    const delta = await screen.findByLabelText('増減ポイント');
    fireEvent.change(delta, { target: { value: '100' } });
    fireEvent.change(await screen.findByLabelText('有効期限'), {
      target: { value: '2026-12-31' },
    });
    fireEvent.change(delta, { target: { value: '-100' } });

    await waitFor(() => expect(screen.queryByLabelText('有効期限')).not.toBeInTheDocument());
    fireEvent.change(screen.getByLabelText('事由'), { target: { value: '失効の補正' } });
    fireEvent.click(screen.getByRole('button', { name: '調整する' }));

    await waitFor(() => expect(mockedAdjust).toHaveBeenCalledTimes(1));
    expect(mockedAdjust.mock.calls[0][1]).toEqual({
      delta: -100,
      reason: '失効の補正',
      idempotency_key: expect.any(String),
    });
  });

  it('成功したら調整後の残高を渡して閉じる', async () => {
    const { onAdjusted, onClose } = renderDialog();

    await submitWith('100');

    await waitFor(() => expect(notify.success).toHaveBeenCalledWith('ポイントを調整しました'));
    expect(onAdjusted).toHaveBeenCalledWith({ linked: true, balance: 220 });
    expect(onClose).toHaveBeenCalled();
  });

  it('失敗後の再送信では同じ冪等キーを送る（サーバが commit 済みの初回を見分ける鍵）', async () => {
    // 応答喪失のとき、この再送信こそが二重記帳の入り口。キーが同じである限りサーバは 2 件目を積まない
    mockedAdjust.mockRejectedValueOnce({
      response: { status: 500, data: { error: '応答がありません' } },
    });
    renderDialog();

    await submitWith('100');
    await waitFor(() => expect(notify.error).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: '調整する' }));

    await waitFor(() => expect(mockedAdjust).toHaveBeenCalledTimes(2));
    const firstKey = mockedAdjust.mock.calls[0][1].idempotency_key;
    expect(firstKey).toEqual(expect.any(String));
    expect(mockedAdjust.mock.calls[1][1].idempotency_key).toBe(firstKey);
  });

  it('失敗後に内容を変えて再送信しても冪等キーは変えない（初回が成立していればサーバが 409 で明告する）', async () => {
    // キーを振り直すと「修正のつもりの再送」が新しい操作になり、初回と合わせて静黙な二重記帳になる
    mockedAdjust.mockRejectedValueOnce({
      response: { status: 500, data: { error: '応答がありません' } },
    });
    renderDialog();

    await submitWith('100');
    await waitFor(() => expect(notify.error).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText('増減ポイント'), { target: { value: '300' } });
    fireEvent.click(screen.getByRole('button', { name: '調整する' }));

    await waitFor(() => expect(mockedAdjust).toHaveBeenCalledTimes(2));
    expect(mockedAdjust.mock.calls[1][1].delta).toBe(300);
    expect(mockedAdjust.mock.calls[1][1].idempotency_key).toBe(
      mockedAdjust.mock.calls[0][1].idempotency_key
    );
  });

  it('閉じて開き直したら新しい冪等キーになる（別の操作として 2 件とも成立してよい）', async () => {
    const props = { customerId: 'c1', onClose: jest.fn(), onAdjusted: jest.fn() };
    const { rerender } = render(<PointAdjustmentDialog {...props} open />);

    await submitWith('100');
    await waitFor(() => expect(mockedAdjust).toHaveBeenCalledTimes(1));

    rerender(<PointAdjustmentDialog {...props} open={false} />);
    rerender(<PointAdjustmentDialog {...props} open />);
    await submitWith('100');

    await waitFor(() => expect(mockedAdjust).toHaveBeenCalledTimes(2));
    expect(mockedAdjust.mock.calls[1][1].idempotency_key).not.toBe(
      mockedAdjust.mock.calls[0][1].idempotency_key
    );
  });

  it('失敗したら、対処方法を含むサーバの文言をそのまま出す', async () => {
    // 残高不足・未紐づけは行動できる文言で返ってくる。汎用文言に潰さない
    mockedAdjust.mockRejectedValue({
      response: { status: 400, data: { error: 'ポイント残高が不足しています（残高: 120）' } },
    });
    const { onAdjusted, onClose } = renderDialog();

    await submitWith('-500');

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith('ポイント残高が不足しています（残高: 120）')
    );
    expect(onAdjusted).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
});
