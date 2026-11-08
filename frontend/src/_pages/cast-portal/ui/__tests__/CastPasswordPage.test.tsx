import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CastPasswordPage } from '../CastPasswordPage';
import { authApi } from '@/entities/user';
import { notify } from '@/shared/notify';

const mockLogout = jest.fn();

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
jest.mock('@/entities/user', () => ({
  authApi: { changePassword: jest.fn() },
  useAuth: () => ({ logout: mockLogout }),
}));

const mockedChangePassword = authApi.changePassword as jest.Mock;
const mockedNotifyError = notify.error as jest.Mock;

function fill(current: string, next: string, confirm: string) {
  fireEvent.change(screen.getByLabelText(/^現在のパスワード/), { target: { value: current } });
  fireEvent.change(screen.getByLabelText(/^新しいパスワード（8文字以上）/), {
    target: { value: next },
  });
  fireEvent.change(screen.getByLabelText(/^新しいパスワード（確認）/), {
    target: { value: confirm },
  });
  fireEvent.click(screen.getByRole('button', { name: 'パスワードを変更する' }));
}

describe('CastPasswordPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('画面名は題名帯に任せ、見出しを重ねない', () => {
    render(<CastPasswordPage />);

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
    expect(
      screen.getByText('変更後は自動的にログアウトされ、新しいパスワードでの再ログインが必要です。')
    ).toBeInTheDocument();
  });

  it('現在・新しい・確認の 3 欄で変更し、理由を添えてログアウトする', async () => {
    mockedChangePassword.mockResolvedValue(undefined);
    render(<CastPasswordPage />);

    fill('oldpass123', 'newpass123', 'newpass123');

    await waitFor(() =>
      expect(mockedChangePassword).toHaveBeenCalledWith({
        current_password: 'oldpass123',
        new_password: 'newpass123',
      })
    );
    await waitFor(() => expect(mockLogout).toHaveBeenCalledWith('password-changed'));
  });

  it('確認が一致しなければ欄の傍に文言を出し、送らない', async () => {
    render(<CastPasswordPage />);

    fill('oldpass123', 'newpass123', 'other12345');

    expect(await screen.findByText('新しいパスワードが一致しません')).toBeInTheDocument();
    expect(mockedChangePassword).not.toHaveBeenCalled();
  });

  it('現在のパスワードの誤りはサーバーの文言で知らせ、押し直せる', async () => {
    mockedChangePassword.mockRejectedValue({
      response: { status: 400, data: { error: '現在のパスワードが正しくありません' } },
    });
    render(<CastPasswordPage />);

    fill('wrongpass1', 'newpass123', 'newpass123');

    await waitFor(() =>
      expect(mockedNotifyError).toHaveBeenCalledWith('現在のパスワードが正しくありません')
    );
    expect(mockLogout).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'パスワードを変更する' })).toBeEnabled();
  });
});
