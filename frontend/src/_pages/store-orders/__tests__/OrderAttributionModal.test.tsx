import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { OrderAttributionModal } from '../ui/OrderAttributionModal';
import { Order, OrderAttribution, orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    attribution: jest.fn(),
    invalidateAttribution: jest.fn(),
    reissueReceiptToken: jest.fn(),
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

const mockedAttribution = orderApi.attribution as jest.Mock;
const mockedInvalidate = orderApi.invalidateAttribution as jest.Mock;
const mockedReissue = orderApi.reissueReceiptToken as jest.Mock;

const completedOrder: Order = {
  id: 'o1',
  status: 'COMPLETED',
  business_date: '2026-08-10',
  customer_name: '山田太郎',
};

const attributed: OrderAttribution = {
  id: 501,
  attributed: true,
  member_code: '123456789012',
  source: 'COMPLETION',
  attributed_at: '2026-08-10T19:00:00Z',
};

const invalidated: OrderAttribution = {
  id: 501,
  attributed: false,
  member_code: '123456789012',
  source: 'COMPLETION',
  attributed_at: '2026-08-10T19:00:00Z',
  invalidated_reason: '別人の来店を取り違えたため',
  invalidated_at: '2026-08-12T10:00:00Z',
};

const renderModal = (onClose = jest.fn()) =>
  render(<OrderAttributionModal order={completedOrder} onClose={onClose} />);

describe('OrderAttributionModal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAttribution.mockResolvedValue(attributed);
  });

  it('閉じている間は帰属の現況を取りに行かない', () => {
    // 一覧に常時 mount されているので、開くまで取りに行くと訂正しない画面が毎回読む
    render(<OrderAttributionModal order={null} onClose={jest.fn()} />);

    expect(mockedAttribution).not.toHaveBeenCalled();
  });

  it('帰属している受注では会員コードと成立の機構を出し、理由を添えて無効化できる', async () => {
    mockedInvalidate.mockResolvedValue(invalidated);
    renderModal();

    expect(await screen.findByText('123456789012')).toBeInTheDocument();
    expect(screen.getByText(/完了時の会員紐づけ/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('無効化の理由'), {
      target: { value: '別人の来店を取り違えたため' },
    });
    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));

    await waitFor(() =>
      expect(mockedInvalidate).toHaveBeenCalledWith('o1', {
        // 受注から導かせず、画面が読み口で得た記録そのものを名指す
        attribution_id: 501,
        reason: '別人の来店を取り違えたため',
      })
    );
    expect(notify.success).toHaveBeenCalledWith('帰属を無効化しました');
  });

  it('理由が空のまま無効化しようとしても要求を飛ばさない', async () => {
    renderModal();
    await screen.findByLabelText('無効化の理由');

    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));

    // 理由はこの訂正の唯一の根拠。空のまま往復させると、サーバの 400 を待つ間に押し直せてしまう
    expect(await screen.findByText('無効化の理由を入力してください')).toBeInTheDocument();
    expect(mockedInvalidate).not.toHaveBeenCalled();
  });

  it.each(['COMPLETION', 'RECEIPT_TOKEN'] as const)(
    '清算の宛先は帰属の会員だと述べ、顧客画面の調整を宛先として案内しない（%s）',
    async source => {
      // 調整の口は「その顧客に現在紐づく会員」を対象に取る一方、清算すべき相手はこの帰属記録が
      // 持つ会員（不変）。ずれた状態で案内どおり操作すると無関係な会員から引かれるため、成立の
      // 機構によらず特定の画面を宛先として案内しない
      mockedAttribution.mockResolvedValue({ ...attributed, source });
      renderModal();

      expect(await screen.findByText(/付与済みのポイントは戻りません/)).toBeInTheDocument();
      expect(screen.getByText(/上の会員コードの台帳に対して行う/)).toBeInTheDocument();
      expect(screen.getByText(/この会員に届きません/)).toBeInTheDocument();
    }
  );

  it('無効化に成功すると、取り直さずに再発行の導線へ切り替わる', async () => {
    mockedInvalidate.mockResolvedValue(invalidated);
    renderModal();

    fireEvent.change(await screen.findByLabelText('無効化の理由'), {
      target: { value: '取り違え' },
    });
    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));

    expect(await screen.findByRole('button', { name: '伝票QRを再発行' })).toBeInTheDocument();
    expect(screen.queryByLabelText('無効化の理由')).not.toBeInTheDocument();
    // 応答が訂正後の現況を持つので、読み込み表示を挟んで取り直さない
    expect(mockedAttribution).toHaveBeenCalledTimes(1);
  });

  it('再発行した伝票の生値を QR が運ぶ', async () => {
    mockedAttribution.mockResolvedValue(invalidated);
    mockedReissue.mockResolvedValue({ receipt_token: 'raw-token-value' });
    renderModal();

    fireEvent.click(await screen.findByRole('button', { name: '伝票QRを再発行' }));

    const qr = await screen.findByTestId('qr');
    // 生値そのものへ退行しても描画は変わらないため、QR が運ぶ値を断言する
    expect(qr).toHaveAttribute('data-value', expect.stringContaining('raw-token-value'));
    expect(qr.getAttribute('data-value')).toContain('/member/receipts#');
  });

  it('QR を出している間は明示のボタン以外で閉じない', async () => {
    mockedAttribution.mockResolvedValue(invalidated);
    mockedReissue.mockResolvedValue({ receipt_token: 'raw-token-value' });
    const onClose = jest.fn();
    renderModal(onClose);

    fireEvent.click(await screen.findByRole('button', { name: '伝票QRを再発行' }));
    await screen.findByTestId('qr');

    fireEvent.keyDown(document.body, { key: 'Escape' });
    // 生値はこの応答にしか無い。誤って閉じると客が来店を取り戻す手段ごと消える
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('帰属したことのない受注では再発行の導線を出さない', async () => {
    mockedAttribution.mockResolvedValue({ attributed: false });
    renderModal();

    expect(await screen.findByText(/訂正の対象ではありません/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '伝票QRを再発行' })).not.toBeInTheDocument();
  });

  it('現況を読めなかったときは領域が失敗を名乗り、訂正の導線を出さない', async () => {
    // 読めなかった現況を「未帰属」で描くと、他人の来店が残っているのに再発行を勧めることになる
    mockedAttribution.mockRejectedValue(new Error('boom'));
    renderModal();

    expect(await screen.findByText('帰属の状況を取得できませんでした')).toBeInTheDocument();
    expect(screen.queryByLabelText('無効化の理由')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '伝票QRを再発行' })).not.toBeInTheDocument();
  });

  it('無効化の失敗はサーバの文言をそのまま知らせ、入力を残す', async () => {
    mockedInvalidate.mockRejectedValue({
      response: { data: { message: 'この受注は会員へ帰属していません' } },
    });
    renderModal();

    fireEvent.change(await screen.findByLabelText('無効化の理由'), {
      target: { value: '取り違え' },
    });
    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith('この受注は会員へ帰属していません')
    );
    expect(screen.getByLabelText('無効化の理由')).toHaveValue('取り違え');
  });
});
