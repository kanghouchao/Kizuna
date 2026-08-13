import { StrictMode } from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberReceiptClaimPage } from '../MemberReceiptClaimPage';
import { memberReceiptApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  memberReceiptApi: { claim: jest.fn() },
}));

const mockedClaim = memberReceiptApi.claim as jest.Mock;

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
    openWithToken('tok3n');
  });

  it('フラグメントのトークンで申領し、取り込めたポイントを出す', async () => {
    mockedClaim.mockResolvedValue({ granted_points: 120 });

    render(<MemberReceiptClaimPage />);

    expect(await screen.findByText('+120 pt')).toBeInTheDocument();
    expect(mockedClaim).toHaveBeenCalledWith('tok3n');
  });

  it('付与の無い伝票でも来店を取り込めたことを伝える', async () => {
    // 0 円完了の伝票は来店の可視化だけが成立する。失敗と読み違えられてはならない
    mockedClaim.mockResolvedValue({ granted_points: 0 });

    render(<MemberReceiptClaimPage />);

    expect(await screen.findByText('来店履歴に取り込みました')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    // 大きな「+0 pt」は、成立した申領を失敗に見せる
    expect(screen.queryByText('+0 pt')).not.toBeInTheDocument();
  });

  it('申領が終わるまでは読み込み中を表示する', () => {
    mockedClaim.mockReturnValue(new Promise(() => {}));

    render(<MemberReceiptClaimPage />);

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });

  it('通信に失敗したら失敗を名乗り、再試行で復帰できる', async () => {
    mockedClaim.mockRejectedValueOnce(new Error('failed'));
    mockedClaim.mockResolvedValue({ granted_points: 80 });

    render(<MemberReceiptClaimPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('取り込めませんでした');

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('+80 pt')).toBeInTheDocument();
  });

  it('申領できない伝票はサーバの理由を出し、再試行は出さない', async () => {
    // 無効・期限切れ・使用済みは同形の 404。何度押しても結果は変わらない
    mockedClaim.mockRejectedValue(httpError(404, 'この伝票は申領できません。'));

    render(<MemberReceiptClaimPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('この伝票は申領できません。');
    expect(within(region).queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
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

    await screen.findByText('+120 pt');
    await waitFor(() => expect(mockedClaim).toHaveBeenCalledTimes(1));
  });
});
