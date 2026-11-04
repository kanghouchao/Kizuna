import { StrictMode } from 'react';
import { render, screen } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import VerifyEmailPage from '../ui/VerifyEmailPage';
import { emailVerificationApi } from '@/entities/user';

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    emailVerificationApi: { confirm: jest.fn() },
  };
});

const mockedConfirm = emailVerificationApi.confirm as jest.Mock;

function badRequest(): AxiosError {
  const headers = new AxiosHeaders();
  return new AxiosError('Bad Request', 'ERR_BAD_REQUEST', { headers }, null, {
    status: 400,
    statusText: 'Bad Request',
    headers,
    config: { headers },
    data: { error: '確認リンクが無効か、有効期限が切れています。' },
  });
}

describe('VerifyEmailPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    window.location.hash = '';
  });

  it('フラグメントのトークンを一度だけ送り、成功を表示してログインへ案内する', async () => {
    window.location.hash = '#raw-verification-token';
    mockedConfirm.mockResolvedValue(undefined);

    // 開発時の Strict Mode は入場の効果を二度走らせる。二度目の送信は消費済みで 400 になり、成功を上書きしてしまう。
    render(
      <StrictMode>
        <VerifyEmailPage />
      </StrictMode>
    );

    await screen.findByRole('heading', { name: 'メールアドレスを確認しました' });
    expect(mockedConfirm).toHaveBeenCalledTimes(1);
    expect(mockedConfirm).toHaveBeenCalledWith('raw-verification-token');
    expect(screen.getByRole('link', { name: 'ログイン画面へ' })).toHaveAttribute(
      'href',
      '/auth/login'
    );
  });

  it('拒否されたトークンでは確認できなかったことを表示する', async () => {
    window.location.hash = '#expired-token';
    mockedConfirm.mockRejectedValue(badRequest());

    render(<VerifyEmailPage />);

    await screen.findByRole('heading', { name: 'メールアドレスを確認できませんでした' });
    expect(
      screen.getByText('確認リンクが無効か、有効期限が切れています。', { exact: false })
    ).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'ログイン画面へ' })).not.toBeInTheDocument();
  });

  it('通信の失敗は時間をおいて開き直すよう案内する', async () => {
    window.location.hash = '#raw-verification-token';
    mockedConfirm.mockRejectedValue(new Error('Network Error'));

    render(<VerifyEmailPage />);

    await screen.findByRole('heading', { name: 'メールアドレスを確認できませんでした' });
    expect(
      screen.getByText('時間をおいて、メールのリンクを開き直してください。', { exact: false })
    ).toBeInTheDocument();
  });

  it('フラグメントが無ければ送らずに確認できなかったことを表示する', async () => {
    render(<VerifyEmailPage />);

    await screen.findByRole('heading', { name: 'メールアドレスを確認できませんでした' });
    expect(mockedConfirm).not.toHaveBeenCalled();
  });

  it('壊れたパーセント符号化でも送らずに確認できなかったことを表示する', async () => {
    window.location.hash = '#%';

    render(<VerifyEmailPage />);

    await screen.findByRole('heading', { name: 'メールアドレスを確認できませんでした' });
    expect(mockedConfirm).not.toHaveBeenCalled();
  });
});
