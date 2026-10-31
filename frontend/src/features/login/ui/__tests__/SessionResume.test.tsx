import { render, screen, waitFor } from '@testing-library/react';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import Cookies from 'js-cookie';
import { authApi } from '@/entities/user';
import type { MeResponse } from '@/entities/user';
import { apiClient, clearSessionToken, getSessionAlert } from '@/shared/api';
import { rememberMemberReturnPath, takeMemberReturnPath } from '@/shared/lib';
import { SessionResume } from '../SessionResume';

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ replace: mockReplace }) }));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return { ...actual, authApi: { ...actual.authApi, me: jest.fn() } };
});

const mockedMe = authApi.me as jest.Mock;

function refreshReplies(reply: { status: number; headers?: Record<string, string> }) {
  apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => {
    const response = {
      data: { token: 't', expires_at: 1, user_id: 7, refresh_expires_at: Date.now() + 60_000 },
      status: reply.status,
      statusText: '',
      headers: reply.headers ?? {},
      config,
    };
    if (reply.status >= 400) throw Object.assign(new Error('HTTP'), { response, config });
    return response;
  }) as AxiosAdapter;
}

const me = (user_type: MeResponse['user_type']): MeResponse => ({
  email: 'user@kizuna.test',
  user_type,
  permissions: [],
  line_linked: false,
});

describe('SessionResume（ログイン画面を開いたときの継続ログイン）', () => {
  const originalAdapter = apiClient.defaults.adapter;

  beforeEach(() => {
    jest.clearAllMocks();
    clearSessionToken();
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    Cookies.remove('session-kind');
  });

  it('リフレッシュに成功すればフォームを出さずに、利用者種別の着地先へ移る', async () => {
    refreshReplies({ status: 200 });
    mockedMe.mockResolvedValue(me('STAFF'));

    render(<SessionResume>フォーム</SessionResume>);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/admin/dashboard/'));
    expect(screen.queryByText('フォーム')).not.toBeInTheDocument();
    expect(Cookies.get('session-kind')).toBe('admin');
  });

  it('会員の戻り先が預けられていれば、着地先はそれを優先する', async () => {
    rememberMemberReturnPath('/member/reservations/new?store=s1');
    refreshReplies({ status: 200 });
    mockedMe.mockResolvedValue(me('MEMBER'));

    render(<SessionResume>フォーム</SessionResume>);

    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith('/member/reservations/new?store=s1')
    );
  });

  it.each(['STAFF', 'CAST'] as const)(
    '会員の戻り先を預けたまま %s のセッションが続いていれば、着地せずにフォームを出し、戻り先も残す',
    async userType => {
      // 会員ポータルの申領を会員以外のセッションで開くと 403 から戻り先を預けてここへ来る。会員で入り直す導線を残す。
      rememberMemberReturnPath('/member/receipts', '#tok3n');
      refreshReplies({ status: 200 });
      mockedMe.mockResolvedValue(me(userType));

      render(<SessionResume>フォーム</SessionResume>);

      expect(await screen.findByText('フォーム')).toBeInTheDocument();
      expect(mockReplace).not.toHaveBeenCalled();
      expect(takeMemberReturnPath()).toBe('/member/receipts#tok3n');
    }
  );

  it.each(['missing', 'expired', 'credential_changed'])(
    'リフレッシュが 401（%s）ならフォームを出し、差し戻しも再ログインの表示もしない',
    async failure => {
      refreshReplies({ status: 401, headers: { 'x-auth-failure': failure } });

      render(<SessionResume>フォーム</SessionResume>);

      expect(await screen.findByText('フォーム')).toBeInTheDocument();
      expect(mockReplace).not.toHaveBeenCalled();
      expect(getSessionAlert()).toBeNull();
    }
  );
});
