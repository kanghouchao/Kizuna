import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { OrderAttributionModal } from '../ui/OrderAttributionModal';
import { Order, OrderAttribution, orderApi } from '@/entities/order';
import { TokenClaims, readTokenClaims } from '@/shared/lib';

// hasPermission は実物のまま（PERM_ 接頭辞の対応も検証対象に含める）
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));

jest.mock('@/entities/order', () => ({
  orderApi: {
    attribution: jest.fn(),
    invalidateAttribution: jest.fn(),
    reissueReceiptToken: jest.fn(),
    attributionCorrection: jest.fn(),
    correctAttributionPoints: jest.fn(),
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
const mockedCorrectionStatus = orderApi.attributionCorrection as jest.Mock;
const mockedCorrect = orderApi.correctAttributionPoints as jest.Mock;
const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;

/** 指定権限を claim（PERM_ 接頭辞）として持つ token claim を返すヘルパ（UI 出し分けは権限ベース）。 */
function claimsWith(permissions: string[]): TokenClaims {
  return {
    authorities: permissions.map(permission => `PERM_${permission}`),
    userType: 'STAFF',
    storeBridge: true,
  };
}

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
  pending_correction_attribution_id: 501,
  pending_correction_member_code: '123456789012',
};

/**
 * 訂正を後回しにしたあと、正しい本人が再発行された伝票を申領し終えた状態。
 *
 * 現況は<b>その人の有効な帰属</b>に入れ替わり、差し引きが要るのは別人の古い記録（501）になる。
 */
const reclaimedByOther: OrderAttribution = {
  id: 502,
  attributed: true,
  member_code: '999999999999',
  source: 'RECEIPT_TOKEN',
  attributed_at: '2026-08-13T09:00:00Z',
  pending_correction_attribution_id: 501,
  pending_correction_member_code: '123456789012',
};

const renderModal = (onClose = jest.fn()) =>
  render(<OrderAttributionModal order={completedOrder} onClose={onClose} />);

describe('OrderAttributionModal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAttribution.mockResolvedValue(attributed);
    mockedCorrectionStatus.mockResolvedValue({ granted_points: 100, corrected_points: 0 });
    mockedReadClaims.mockReturnValue(claimsWith(['ORDER_MANAGE', 'POINT_ADJUST']));
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
    '無効化だけではポイントが戻らないことを操作の手前で述べる（%s）',
    async source => {
      // 台帳へ波及しないことを事後に知ると、誤付与が残ったまま訂正が済んだと見なされる。
      // 成立の機構によらず同じ文言を出す — どちらの経路でも台帳は動かない
      mockedAttribution.mockResolvedValue({ ...attributed, source });
      renderModal();

      expect(await screen.findByText(/付与済みのポイントは自動では戻りません/)).toBeInTheDocument();
      expect(screen.getByText(/差し引く手続きへ進みます/)).toBeInTheDocument();
    }
  );

  it('無効化に成功すると、そのまま差し引きの段へ送られること', async () => {
    mockedInvalidate.mockResolvedValue(invalidated);
    renderModal();

    fireEvent.change(await screen.findByLabelText('無効化の理由'), {
      target: { value: '取り違え' },
    });
    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));

    // ここで通常画面へ戻すと、誤って付与された分が相手の台帳に残ったまま「訂正が済んだ」ことになり、
    // やり残しに気づく機会がどこにも無くなる
    expect(await screen.findByLabelText('差し引くポイント')).toBeInTheDocument();
    expect(screen.queryByLabelText('無効化の理由')).not.toBeInTheDocument();
    // 宛先は今まさに倒した記録そのもの。受注から導き直さない
    expect(mockedCorrectionStatus).toHaveBeenCalledWith('o1', 501);
    // 応答が訂正後の現況を持つので、読み込み表示を挟んで取り直さない
    expect(mockedAttribution).toHaveBeenCalledTimes(1);
  });

  it('差し引きは倒した帰属記録を名指し、付与の全額を既定値に取ること', async () => {
    mockedInvalidate.mockResolvedValue(invalidated);
    mockedCorrect.mockResolvedValue({ granted_points: 100, corrected_points: 100 });
    renderModal();

    fireEvent.change(await screen.findByLabelText('無効化の理由'), {
      target: { value: '取り違え' },
    });
    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));

    // 取得できてからフォームを起こすので、既定値は最初に描かれたフレームから入っている
    expect(await screen.findByLabelText('差し引くポイント')).toHaveValue(100);

    // 差し引きが済めば残りの誤付与は無くなる。畳んだ後に現況を取り直すので、その応答を用意する
    mockedAttribution.mockResolvedValue({
      ...invalidated,
      pending_correction_attribution_id: undefined,
      pending_correction_member_code: undefined,
    });
    fireEvent.change(screen.getByLabelText('訂正の理由'), { target: { value: '誤付与の訂正' } });
    fireEvent.click(screen.getByRole('button', { name: '差し引く' }));

    await waitFor(() =>
      expect(mockedCorrect).toHaveBeenCalledWith('o1', {
        // 顧客に今紐づく会員ではなく、この帰属記録が持つ会員へ届かせる
        attribution_id: 501,
        points: 100,
        reason: '誤付与の訂正',
        idempotency_key: expect.any(String),
      })
    );
    // 差し引きを終えると通常の画面へ戻り、正しい本人への再発行に進める
    expect(await screen.findByRole('button', { name: '伝票QRを再発行' })).toBeInTheDocument();
  });

  it('付与の無い来店では差し引く欄を出さないこと', async () => {
    mockedInvalidate.mockResolvedValue(invalidated);
    mockedCorrectionStatus.mockResolvedValue({ granted_points: 0, corrected_points: 0 });
    renderModal();

    fireEvent.change(await screen.findByLabelText('無効化の理由'), {
      target: { value: '取り違え' },
    });
    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));

    expect(await screen.findByText(/差し引く分はありません/)).toBeInTheDocument();
    expect(screen.queryByLabelText('差し引くポイント')).not.toBeInTheDocument();
  });

  describe('差し引くポイントの検証', () => {
    /** 差し引きの段まで進める（無効化 → 訂正の段）。 */
    const openCorrectionStep = async () => {
      mockedInvalidate.mockResolvedValue(invalidated);
      renderModal();
      fireEvent.change(await screen.findByLabelText('無効化の理由'), {
        target: { value: '取り違え' },
      });
      fireEvent.click(screen.getByRole('button', { name: '無効化する' }));
      return screen.findByLabelText('差し引くポイント');
    };

    it('空欄のまま送っても要求を飛ばさないこと', async () => {
      // 空欄の valueAsNumber は NaN で、null でも空文字でもないため required は素通りする。
      // そのまま送ると JSON の null になり、欄の傍ではなくサーバの失敗として返ってくる
      const input = await openCorrectionStep();
      fireEvent.change(input, { target: { value: '' } });
      fireEvent.change(screen.getByLabelText('訂正の理由'), { target: { value: '誤付与の訂正' } });
      fireEvent.click(screen.getByRole('button', { name: '差し引く' }));

      expect(await screen.findByText('差し引くポイントを入力してください')).toBeInTheDocument();
      expect(mockedCorrect).not.toHaveBeenCalled();
    });

    it('小数のまま送っても要求を飛ばさないこと', async () => {
      // noValidate は type="number" の暗黙の step=1 も止める。これが無いと 1.5 が int の points へ届く
      const input = await openCorrectionStep();
      fireEvent.change(input, { target: { value: '1.5' } });
      fireEvent.change(screen.getByLabelText('訂正の理由'), { target: { value: '誤付与の訂正' } });
      fireEvent.click(screen.getByRole('button', { name: '差し引く' }));

      expect(
        await screen.findByText('差し引くポイントは整数で入力してください')
      ).toBeInTheDocument();
      expect(mockedCorrect).not.toHaveBeenCalled();
    });
  });

  it('再発行が前の QR を殺すことを、押す手前で名乗る', async () => {
    // 2 度目の再発行は前に渡した QR を失効させる。押した後では取り戻せない副作用なので、
    // ボタンと同じ画面に出ていなければ操作者は自覚しないまま客の手元の QR を殺す
    mockedAttribution.mockResolvedValue(invalidated);
    renderModal();

    expect(await screen.findByText(/前に発行した伝票QRは使えなくなります/)).toBeInTheDocument();
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

  it('正しい本人が申領を済ませた後でも、残っている誤付与の差し引きへ戻れること', async () => {
    // 後回しにした差し引きは、その間に現況が別人の有効な帰属へ入れ替わると受注からは名指せなくなる。
    // ここに導線が無いと、誤付与は相手の台帳に残ったまま二度と辿り着けない
    mockedAttribution.mockResolvedValue(reclaimedByOther);
    renderModal();

    expect(await screen.findByText(/差し引かれていないポイントが残っています/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'ポイントを差し引く' }));

    // 宛先はサーバが名指す古い記録（501）。現況の 502 を訂正すると、取り戻したばかりの来店を消す
    await waitFor(() => expect(mockedCorrectionStatus).toHaveBeenCalledWith('o1', 501));
    expect(await screen.findByText(/123456789012 の台帳から差し引きます/)).toBeInTheDocument();
  });

  it('残っている誤付与が無ければ差し引きへの導線を出さないこと', async () => {
    mockedAttribution.mockResolvedValue({
      ...invalidated,
      pending_correction_attribution_id: undefined,
      pending_correction_member_code: undefined,
    });
    renderModal();

    await screen.findByText(/前に発行した伝票QRは使えなくなります/);
    expect(screen.queryByRole('button', { name: 'ポイントを差し引く' })).not.toBeInTheDocument();
  });

  it('ポイント調整の権限が無ければ無効化の導線も差し引きの導線も出さないこと', async () => {
    // 無効化はこの PR で POINT_ADJUST 限定になった。押せる導線として描くと、理由を入力し終えてから
    // 拒否されることになる
    mockedReadClaims.mockReturnValue(claimsWith(['ORDER_MANAGE']));
    mockedAttribution.mockResolvedValue(reclaimedByOther);
    renderModal();

    expect(await screen.findByText(/ポイント調整の権限が必要です/)).toBeInTheDocument();
    expect(screen.queryByLabelText('無効化の理由')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'ポイントを差し引く' })).not.toBeInTheDocument();
  });

  it('差し引きの送信中は明示のボタン以外で閉じないこと', async () => {
    // 送信中に閉じると、訂正が成立したか分からないまま冪等キーが失われる。開き直しでは別のキーになり、
    // 二重に引かれたのかどうかを操作者が判じられない
    mockedInvalidate.mockResolvedValue(invalidated);
    let settle: (value: unknown) => void = () => {};
    mockedCorrect.mockReturnValue(
      new Promise(resolve => {
        settle = resolve;
      })
    );
    const onClose = jest.fn();
    renderModal(onClose);

    fireEvent.change(await screen.findByLabelText('無効化の理由'), {
      target: { value: '取り違え' },
    });
    fireEvent.click(screen.getByRole('button', { name: '無効化する' }));
    await screen.findByLabelText('差し引くポイント');
    fireEvent.change(screen.getByLabelText('訂正の理由'), { target: { value: '誤付与の訂正' } });
    fireEvent.click(screen.getByRole('button', { name: '差し引く' }));

    await screen.findByRole('button', { name: '処理中...' });
    fireEvent.keyDown(document.body, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();

    settle({ granted_points: 100, corrected_points: 100 });
    await waitFor(() => expect(notify.success).toHaveBeenCalled());
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
