import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { toast } from 'react-hot-toast';
import { MemberRegisterForm } from '../MemberRegisterForm';
import { memberApi } from '@/entities/member';
import { platformAuthApi } from '@/entities/user';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  toast: { error: jest.fn() },
}));

jest.mock('@/entities/member', () => ({
  memberApi: { register: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    platformAuthApi: {
      ...actual.platformAuthApi,
      login: jest.fn(),
    },
  };
});

const mockedRegister = memberApi.register as jest.Mock;
const mockedLogin = platformAuthApi.login as jest.Mock;
const mockedToastError = toast.error as jest.Mock;

async function submitForm() {
  render(<MemberRegisterForm />);
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'member@example.com' },
  });
  fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'password1234' } });
  fireEvent.change(screen.getByLabelText('表示名'), { target: { value: '会員花子' } });
  fireEvent.click(screen.getByRole('button', { name: '登録する' }));
}

describe('MemberRegisterForm', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterEach(() => {
    Cookies.remove('token');
    Cookies.remove('platform-role');
  });

  it('登録成功後にそのままログインし、member セッションで /member/ へ遷移する', async () => {
    mockedRegister.mockResolvedValue({ member_code: '123456789012' });
    mockedLogin.mockResolvedValue({ token: 'jwt-token', expires_at: Date.now() + 3600_000 });

    await submitForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/'));
    expect(mockedRegister).toHaveBeenCalledWith({
      email: 'member@example.com',
      password: 'password1234',
      display_name: '会員花子',
    });
    expect(mockedLogin).toHaveBeenCalledWith(
      { email: 'member@example.com', password: 'password1234' },
      { skipAuthRedirect: true }
    );
    expect(Cookies.get('token')).toBe('jwt-token');
    expect(Cookies.get('platform-role')).toBe('member');
    expect(mockedToastError).not.toHaveBeenCalled();
  });

  it('登録に失敗したらエラートーストを出し、ログインは試みない', async () => {
    mockedRegister.mockRejectedValue(new Error('duplicate'));

    await submitForm();

    await waitFor(() => expect(mockedToastError).toHaveBeenCalled());
    expect(mockedLogin).not.toHaveBeenCalled();
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('登録は成功したが自動ログインに失敗したらログイン画面へ誘導する', async () => {
    mockedRegister.mockResolvedValue({ member_code: '123456789012' });
    mockedLogin.mockRejectedValue(new Error('login failed'));

    await submitForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/platform/login'));
    expect(mockedToastError).toHaveBeenCalledWith('登録が完了しました。ログインしてください');
  });
});
