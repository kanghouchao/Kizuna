import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import toast from 'react-hot-toast';
import PlatformLoginForm from '../PlatformLoginForm';
import { platformAuthApi } from '@/entities/user';
import type { PlatformMeResponse } from '@/entities/user';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { error: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    platformAuthApi: {
      ...actual.platformAuthApi,
      login: jest.fn(),
      me: jest.fn(),
    },
  };
});

const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;
const mockedToastError = toast.error as jest.Mock;

function meResponse(overrides: Partial<PlatformMeResponse>): PlatformMeResponse {
  return {
    email: 'user@kizuna.test',
    display_name: '本人',
    user_type: 'STAFF',
    capabilities: [],
    console: 'none',
    store_bridge: false,
    store_scope_type: 'ALL_STORES',
    store_ids: [],
    ...overrides,
  };
}

async function submitLoginForm() {
  render(<PlatformLoginForm />);
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'user@kizuna.test' },
  });
  fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
  fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));
}

describe('PlatformLoginForm CAST/MEMBER 分岐（#328）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.login.mockResolvedValue({
      token: 'jwt-token',
      expires_at: Date.now() + 60 * 60 * 1000,
    });
  });

  afterEach(() => {
    Cookies.remove('token');
    Cookies.remove('platform-role');
    Cookies.remove('platform-store-id');
  });

  it('user_type=CAST は cast セッションを開始し /cast/schedule/ へ遷移する', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'CAST', console: 'none' }));

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/cast/schedule/'));
    expect(Cookies.get('platform-role')).toBe('cast');
    expect(Cookies.get('token')).toBe('jwt-token');
    expect(mockedToastError).not.toHaveBeenCalled();
  });

  it('user_type=MEMBER は準備中トーストのままセッションを破棄する（現状維持）', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER', console: 'none' }));

    await submitLoginForm();

    await waitFor(() =>
      expect(mockedToastError).toHaveBeenCalledWith('この利用者種別のポータルは準備中です')
    );
    expect(mockPush).not.toHaveBeenCalled();
    expect(Cookies.get('token')).toBeUndefined();
    expect(Cookies.get('platform-role')).toBeUndefined();
  });
});
