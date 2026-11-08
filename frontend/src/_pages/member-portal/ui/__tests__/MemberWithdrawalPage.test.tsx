import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberWithdrawalPage } from '../MemberWithdrawalPage';
import { memberApi } from '@/entities/member';
import { notify } from '@/shared/notify';

jest.mock('@/entities/member', () => ({
  memberApi: { withdraw: jest.fn(), home: jest.fn() },
}));

const mockLogout = jest.fn();
jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: mockLogout }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedWithdraw = memberApi.withdraw as jest.Mock;

const submitButton = () => screen.getByRole('button', { name: '退会する' });
const understood = () => screen.getByRole('checkbox', { name: '上記の内容を理解しました' });
const acknowledge = () => fireEvent.click(understood());
const typeReason = (value: string) =>
  fireEvent.change(screen.getByLabelText('退会理由'), { target: { value } });

describe('MemberWithdrawalPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // 取得の結果は画面に出ないので決着させない（決着後の再描画を各検証で待たずに済む）
    jest.mocked(memberApi.home).mockReturnValue(new Promise(() => {}));
  });

  it('開いた時点で本人を取得する（未ログインを 401 でログインへ送る入口）', () => {
    // 退会の送信（POST）は未ログインだと CSRF の 403 で止まり、401 のログイン導線へ届かない
    render(<MemberWithdrawalPage />);

    expect(memberApi.home).toHaveBeenCalledTimes(1);
  });

  it('退会で起きることを説明し、「理解しました」に印を付けるまで実行ボタンを開かない', () => {
    render(<MemberWithdrawalPage />);

    expect(screen.getByRole('heading', { name: '退会すると', level: 2 })).toBeInTheDocument();
    expect(screen.getByText(/ポイントはすべて消去され/)).toBeInTheDocument();
    expect(screen.getByText(/新しい会員として始まります/)).toBeInTheDocument();
    expect(understood()).not.toBeChecked();
    expect(submitButton()).toBeDisabled();

    acknowledge();
    expect(understood()).toBeChecked();
    expect(submitButton()).toBeEnabled();

    acknowledge();
    expect(submitButton()).toBeDisabled();
  });

  it('理由が空なら送信せず、入力横に文言を出す', async () => {
    render(<MemberWithdrawalPage />);
    acknowledge();

    fireEvent.click(submitButton());

    expect(await screen.findByText('退会理由を入力してください')).toBeInTheDocument();
    expect(mockedWithdraw).not.toHaveBeenCalled();
    expect(mockLogout).not.toHaveBeenCalled();
  });

  it('成功したら理由だけを送り、通知は出さずに理由コード付きでログアウトする', async () => {
    mockedWithdraw.mockResolvedValue(undefined);
    render(<MemberWithdrawalPage />);
    typeReason('引っ越しのため');
    acknowledge();

    fireEvent.click(submitButton());

    await waitFor(() => expect(mockLogout).toHaveBeenCalledWith('member-withdrawn'));
    expect(mockedWithdraw).toHaveBeenCalledWith({ reason: '引っ越しのため' });
    expect(notify.success).not.toHaveBeenCalled();
    // ログアウトの遷移中は再び押せない
    expect(screen.getByRole('button', { name: '手続き中...' })).toBeDisabled();
  });

  it('失敗したらサーバの文言で知らせ、ログアウトせず画面に入力を残す', async () => {
    mockedWithdraw.mockRejectedValue({
      response: { status: 409, data: { error: 'この会員は既に退会しています' } },
    });
    render(<MemberWithdrawalPage />);
    typeReason('やめる');
    acknowledge();

    fireEvent.click(submitButton());

    await waitFor(() => expect(notify.error).toHaveBeenCalledWith('この会員は既に退会しています'));
    expect(mockLogout).not.toHaveBeenCalled();
    expect(screen.getByLabelText('退会理由')).toHaveValue('やめる');
    expect(submitButton()).toBeEnabled();
  });
});
