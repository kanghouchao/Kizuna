import { StrictMode } from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberReceiptClaimPage } from '../MemberReceiptClaimPage';
import { memberReceiptApi } from '@/entities/order';
import { memberPointApi } from '@/entities/point';
import { redirectToLogin, rememberMemberReturnPath } from '@/shared/lib';

jest.mock('@/entities/order', () => ({
  memberReceiptApi: { claim: jest.fn() },
}));

jest.mock('@/entities/point', () => ({
  memberPointApi: { balance: jest.fn() },
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  redirectToLogin: jest.fn(),
  rememberMemberReturnPath: jest.fn(),
}));

const mockedClaim = memberReceiptApi.claim as jest.Mock;
const mockedBalance = memberPointApi.balance as jest.Mock;

/** 伝票の QR は申領 URL のフラグメントにトークンを載せて運ぶ。 */
const openWithToken = (token: string) =>
  window.history.pushState({}, '', `/member/receipts#${encodeURIComponent(token)}`);

const httpError = (status: number, message: string) => ({
  response: { status, data: { error: message } },
});

describe('MemberReceiptClaimPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedClaim.mockResolvedValue({ granted_points: 0 });
    mockedBalance.mockResolvedValue({ balance: 1320 });
    openWithToken('tok3n');
  });

  it('フラグメントのトークンで申領し、付与されたポイントと申領後の残高とポイントへの導線を出す', async () => {
    mockedClaim.mockResolvedValue({ granted_points: 120 });

    render(<MemberReceiptClaimPage />);

    expect(await screen.findByText('+120 ポイント')).toBeInTheDocument();
    expect(mockedClaim).toHaveBeenCalledWith('tok3n');
    expect(await screen.findByText('1,320 ポイント')).toBeInTheDocument();
    expect(screen.getByText('現在の残高')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ポイントを見る' })).toHaveAttribute(
      'href',
      '/member/points'
    );
  });

  it('残高は申領が成立してから取る', async () => {
    // 申領と並べて取ると、付与前の残高を「現在の残高」として出しうる
    let resolveClaim: (value: { granted_points: number }) => void = () => {};
    mockedClaim.mockReturnValue(new Promise(resolve => (resolveClaim = resolve)));

    render(<MemberReceiptClaimPage />);

    await waitFor(() => expect(mockedClaim).toHaveBeenCalledTimes(1));
    expect(mockedBalance).not.toHaveBeenCalled();

    resolveClaim({ granted_points: 120 });

    expect(await screen.findByText('1,320 ポイント')).toBeInTheDocument();
    expect(mockedBalance).toHaveBeenCalledTimes(1);
  });

  it('残高だけが読めなくても、付与されたポイントは出したまま残高の区画が失敗を名乗る', async () => {
    mockedClaim.mockResolvedValue({ granted_points: 120 });
    mockedBalance.mockRejectedValueOnce(new Error('failed'));

    render(<MemberReceiptClaimPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('残高を取得できませんでした');
    expect(screen.getByText('+120 ポイント')).toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('1,320 ポイント')).toBeInTheDocument();
    // 再試行は残高だけを読み直す。申領を二度投げると成立した申領が失敗に化ける
    expect(mockedClaim).toHaveBeenCalledTimes(1);
  });

  it('別コンソールのセッション（403）ではトークンごと戻り先を預けてログインへ送る', async () => {
    // 会員以外のセッションが残ったまま QR を開いた場面。ここで失敗を出すと、フラグメントの
    // トークンはログインの往復で失われ、申領する手段が無くなる。
    mockedClaim.mockRejectedValue(httpError(403, 'Forbidden'));

    render(<MemberReceiptClaimPage />);

    await waitFor(() => expect(redirectToLogin).toHaveBeenCalledTimes(1));
    expect(rememberMemberReturnPath).toHaveBeenCalledWith('/member/receipts', '#tok3n');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('付与の無い伝票でも来店を取り込めたことを伝える', async () => {
    // 0 円完了の伝票は来店の可視化だけが成立する。失敗と読み違えられてはならない
    mockedClaim.mockResolvedValue({ granted_points: 0 });

    render(<MemberReceiptClaimPage />);

    expect(await screen.findByText('来店履歴に取り込みました')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    // 大きな「+0 pt」は、成立した申領を失敗に見せる
    expect(screen.queryByText('+0 ポイント')).not.toBeInTheDocument();
  });

  it('申領が終わるまでは読み込み中を表示する', () => {
    mockedClaim.mockReturnValue(new Promise(() => {}));

    render(<MemberReceiptClaimPage />);

    expect(screen.getByRole('status', { name: '読み込み中' })).toBeInTheDocument();
    expect(mockedBalance).not.toHaveBeenCalled();
  });

  it('通信に失敗したら失敗を名乗り、再試行で復帰できる', async () => {
    mockedClaim.mockRejectedValueOnce(new Error('failed'));
    mockedClaim.mockResolvedValue({ granted_points: 80 });

    render(<MemberReceiptClaimPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('取り込めませんでした');

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('+80 ポイント')).toBeInTheDocument();
  });

  it('申領できない伝票はサーバの理由を出し、再試行は出さない', async () => {
    // 無効・期限切れ・使用済みは同形の 404。何度押しても結果は変わらない
    mockedClaim.mockRejectedValue(httpError(404, 'この伝票は申領できません。'));

    render(<MemberReceiptClaimPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('この伝票は申領できません。');
    expect(within(region).queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    // 伝票の取り直しは店舗に尋ねるしかない。この画面の上位のポイントへ戻す
    expect(within(region).getByRole('link', { name: 'ポイントへ' })).toHaveAttribute(
      'href',
      '/member/points'
    );
  });

  it('通信失敗の後の再試行が申領不能で返ったら、結果不明として履歴の確認を促す', async () => {
    // 応答だけを取り落とした場合（サーバは申領済み）、再試行は同形の 404 で返る。これを
    // 「申領できません」と言い切ると、実際には取り込めている来店を失敗と読ませてしまう。
    // 伝票は二度と使えないので、利用者が確かめられる先を出すしかない
    mockedClaim.mockRejectedValueOnce(new Error('network'));
    mockedClaim.mockRejectedValue(httpError(404, 'この伝票は申領できません。'));

    render(<MemberReceiptClaimPage />);

    const region = await screen.findByRole('alert');
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    const failed = await screen.findByRole('alert');
    expect(failed).toHaveTextContent('取り込めたかどうかを確認できませんでした');
    expect(within(failed).getByRole('link', { name: '来店履歴へ' })).toBeInTheDocument();
    expect(within(failed).queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
  });

  it('初回から申領不能で返った伝票は、結果不明ではなく申領不能として出す', async () => {
    // 一度も届いていない要求に「取り込めたかもしれません」と言うのは、別方向の嘘になる
    mockedClaim.mockRejectedValue(httpError(404, 'この伝票は申領できません。'));

    render(<MemberReceiptClaimPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('この伝票は申領できません。');
    expect(region).not.toHaveTextContent('確認できませんでした');
  });

  it('トークンが無ければ申領を投げず、読み取り直しを促す', async () => {
    window.history.pushState({}, '', '/member/receipts');

    render(<MemberReceiptClaimPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('伝票の QR');
    expect(mockedClaim).not.toHaveBeenCalled();
  });

  it('効果の再実行でも申領は 1 度しか投げない', async () => {
    // 申領は取り返しがつかない上に二度目は同形のエラーになる。二重に投げると、成立した申領が
    // 失敗の画面に化ける（StrictMode の二重 mount は開発時に必ず起きる）
    mockedClaim.mockResolvedValue({ granted_points: 120 });

    render(
      <StrictMode>
        <MemberReceiptClaimPage />
      </StrictMode>
    );

    await screen.findByText('+120 ポイント');
    await waitFor(() => expect(mockedClaim).toHaveBeenCalledTimes(1));
  });
});
