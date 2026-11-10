import { render, screen, waitFor } from '@testing-library/react';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import Cookies from 'js-cookie';
import { authApi } from '@/entities/user';
import type { MeResponse } from '@/entities/user';
import { apiClient, endSession, getSessionAlert } from '@/shared/api';
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
  email_verified: false,
});

describe('SessionResume（ログイン画面を開いたときの継続ログイン）', () => {
  const originalAdapter = apiClient.defaults.adapter;

  beforeEach(() => {
    jest.clearAllMocks();
    endSession('resume-failed');
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    Cookies.remove('session-kind');
    window.history.pushState({}, '', '/');
  });

  it('リフレッシュに成功すればフォームを出さずに、利用者種別の着地先へ移る', async () => {
    refreshReplies({ status: 200 });
    mockedMe.mockResolvedValue(me('STAFF'));

    render(<SessionResume>フォーム</SessionResume>);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/admin/dashboard/'));
    expect(screen.queryByText('フォーム')).not.toBeInTheDocument();
    expect(Cookies.get('session-kind')).toBe('admin');
  });

  it('ログイン画面の next が本人種別の戻り先なら、着地先はそれを断片ごと優先する', async () => {
    window.history.pushState(
      {},
      '',
      '/auth/login?next=%2Fmember%2Freservations%2Fnew%3Fstore%3Ds1#tok3n'
    );
    refreshReplies({ status: 200 });
    mockedMe.mockResolvedValue(me('MEMBER'));

    render(<SessionResume>フォーム</SessionResume>);

    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith('/member/reservations/new?store=s1#tok3n')
    );
  });

  it('next が別の本人種別のパスなら、既定の着地へ進む', async () => {
    window.history.pushState({}, '', '/auth/login?next=%2Fcast%2F');
    refreshReplies({ status: 200 });
    mockedMe.mockResolvedValue(me('STAFF'));

    render(<SessionResume>フォーム</SessionResume>);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/admin/dashboard/'));
  });

  it.each(['STAFF', 'CAST'] as const)(
    '会員の画面への next を持ったまま %s のセッションが続いていれば、着地せずにフォームを出す',
    async userType => {
      // 会員ポータルの申領を会員以外のセッションで開くと 403 から next を載せてここへ来る。会員で入り直す導線を残す。
      window.history.pushState({}, '', '/auth/login?next=%2Fmember%2Freceipts#tok3n');
      refreshReplies({ status: 200 });
      mockedMe.mockResolvedValue(me(userType));

      render(<SessionResume>フォーム</SessionResume>);

      expect(await screen.findByText('フォーム')).toBeInTheDocument();
      expect(mockReplace).not.toHaveBeenCalled();
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
