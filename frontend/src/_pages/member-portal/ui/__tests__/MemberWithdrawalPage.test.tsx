import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberWithdrawalPage } from '../MemberWithdrawalPage';
import { memberApi } from '@/entities/member';
import { notify } from '@/shared/notify';

jest.mock('@/entities/member', () => ({
  memberApi: { withdraw: jest.fn() },
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
const typeConfirmation = (value: string) =>
  fireEvent.change(screen.getByLabelText('確認のため「退会」と入力'), { target: { value } });
const typeReason = (value: string) =>
  fireEvent.change(screen.getByLabelText('退会理由'), { target: { value } });

describe('MemberWithdrawalPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('退会で起きることを説明し、確認欄に「退会」と打つまで実行ボタンを開かない', () => {
    render(<MemberWithdrawalPage />);

    expect(screen.getByRole('heading', { name: '退会すると', level: 2 })).toBeInTheDocument();
    expect(screen.getByText(/ポイントはすべて消去され/)).toBeInTheDocument();
    expect(screen.getByText(/新しい会員として始まります/)).toBeInTheDocument();
    expect(submitButton()).toBeDisabled();

    typeConfirmation('退 会');
    expect(submitButton()).toBeDisabled();

    typeConfirmation('退会');
    expect(submitButton()).toBeEnabled();
  });

  it('理由が空なら送信せず、入力横に文言を出す', async () => {
    render(<MemberWithdrawalPage />);
    typeConfirmation('退会');

    fireEvent.click(submitButton());

    expect(await screen.findByText('退会理由を入力してください')).toBeInTheDocument();
    expect(mockedWithdraw).not.toHaveBeenCalled();
    expect(mockLogout).not.toHaveBeenCalled();
  });

  it('成功したら理由だけを送り、通知は出さずに理由コード付きでログアウトする', async () => {
    mockedWithdraw.mockResolvedValue(undefined);
    render(<MemberWithdrawalPage />);
    typeReason('引っ越しのため');
    typeConfirmation('退会');

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
    typeConfirmation('退会');

    fireEvent.click(submitButton());

    await waitFor(() => expect(notify.error).toHaveBeenCalledWith('この会員は既に退会しています'));
    expect(mockLogout).not.toHaveBeenCalled();
    expect(screen.getByLabelText('退会理由')).toHaveValue('やめる');
    expect(submitButton()).toBeEnabled();
  });

  it('ホームへ戻る導線を張る', () => {
    render(<MemberWithdrawalPage />);

    expect(screen.getByRole('link', { name: 'ホームへ戻る' })).toHaveAttribute('href', '/member');
  });
});
