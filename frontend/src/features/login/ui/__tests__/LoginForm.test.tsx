import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { notify } from '@/shared/notify';
import LoginForm from '../LoginForm';
import { authApi } from '@/entities/user';
import { endSession, getSessionUserId } from '@/shared/api';
import type { MeResponse } from '@/entities/user';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    authApi: {
      ...actual.authApi,
      login: jest.fn(),
      me: jest.fn(),
    },
  };
});

const mockedAuthApi = authApi as jest.Mocked<typeof authApi>;
const mockedNotifyError = notify.error as jest.Mock;

function meResponse(overrides: Partial<MeResponse>): MeResponse {
  return {
    email: 'user@kizuna.test',
    display_name: '本人',
    user_type: 'STAFF',
    permissions: [],
    line_linked: false,
    email_verified: false,
    ...overrides,
  };
}

/** ログイン画面の URL（next と断片）を開いた状態にする。 */
const openLogin = (searchAndHash: string) =>
  window.history.pushState({}, '', `/auth/login${searchAndHash}`);

async function submitLoginForm() {
  render(<LoginForm />);
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'user@kizuna.test' },
  });
  fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
  fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));
}

describe('LoginForm の利用者種別による分岐', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.login.mockResolvedValue({
      token: 'jwt-token',
      expires_at: Date.now() + 15 * 60 * 1000,
      user_id: 7,
      refresh_expires_at: Date.now() + 14 * 24 * 60 * 60 * 1000,
    });
  });

  afterEach(() => {
    endSession('resume-failed');
    Cookies.remove('session-kind');
    window.history.pushState({}, '', '/');
  });

  it('user_type=STAFF は admin セッションを開始し /admin（着地の画面）へ遷移する', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'STAFF' }));

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin'));
    expect(Cookies.get('session-kind')).toBe('admin');
    expect(getSessionUserId()).toBe(7);
  });

  it('user_type が想定外ならセッションを破棄し遷移しない', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: undefined }));

    await submitLoginForm();

    await waitFor(() => expect(mockedNotifyError).toHaveBeenCalled());
    expect(mockPush).not.toHaveBeenCalled();
    expect(Cookies.get('session-kind')).toBeUndefined();
    expect(getSessionUserId()).toBeUndefined();
  });

  it('user_type=CAST は cast セッションを開始し /cast/ へ遷移する', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'CAST' }));

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/cast/'));
    expect(Cookies.get('session-kind')).toBe('cast');
    expect(getSessionUserId()).toBe(7);
    expect(mockedNotifyError).not.toHaveBeenCalled();
  });

  it('会員が公式サイトの予約導線から弾かれていた場合、next の画面へ戻ること', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER' }));
    openLogin('?next=%2Fmember%2Freservations%2Fnew%3Fstore%3Dstore1.kizuna.test');

    await submitLoginForm();

    await waitFor(() =>
      expect(mockPush).toHaveBeenCalledWith('/member/reservations/new?store=store1.kizuna.test')
    );
  });

  it('伝票の QR から来ていた場合、ログイン画面の断片ごと申領画面へ戻ること', async () => {
    // 断片はサーバへ送られない。申領に要るトークンはこれしか無く、落とすと戻れても申領できない
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER' }));
    openLogin('?next=%2Fmember%2Freceipts#tok3n');

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/receipts#tok3n'));
  });

  it('従業員も next の画面へ戻ること', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'STAFF' }));
    openLogin('?reason=expired&next=%2Fstore%2F1%2Fcasts');

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/1/casts'));
  });

  it('別の本人種別の next は無視し、既定の着地へ進むこと', async () => {
    // 申領画面は開いた時点で取り込むため、会員以外のログインで会員の画面へ運ばない
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'CAST' }));
    openLogin('?next=%2Fmember%2Freceipts#tok3n');

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/cast/'));
  });

  it.each(['https://evil.test/', '//evil.test/member/'])(
    'next に %s が仕込まれていても既定のホームへ遷移すること',
    async next => {
      mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER' }));
      openLogin(`?next=${encodeURIComponent(next)}`);

      await submitLoginForm();

      await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/'));
    }
  );

  it('会員の戻り先 cookie を発行しないこと', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER' }));
    openLogin('?next=%2Fmember%2Freceipts#tok3n');

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalled());
    expect(Cookies.get('member-return-path')).toBeUndefined();
  });

  it('user_type=MEMBER は member セッションを開始し /member/ へ遷移する', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER' }));

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/'));
    expect(Cookies.get('session-kind')).toBe('member');
    expect(getSessionUserId()).toBe(7);
    expect(mockedNotifyError).not.toHaveBeenCalled();
  });
});

describe('LoginForm のクライアント検証（#598）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('未入力のまま押すと欄ごとの文言が出て、送信は行われないこと', async () => {
    render(<LoginForm />);

    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(await screen.findByText('メールアドレスを入力してください')).toBeInTheDocument();
    expect(screen.getByText('パスワードを入力してください')).toBeInTheDocument();
    expect(mockedAuthApi.login).not.toHaveBeenCalled();
    // 検証を理由にボタンを塞がない（押させて文言を出す）
    expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled();
  });

  it('文言が欄と結び付いていること（読み上げ環境で「どの欄が」まで届く）', async () => {
    render(<LoginForm />);

    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    const message = await screen.findByText('メールアドレスを入力してください');
    const input = screen.getByLabelText('メールアドレス');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-describedby', message.id);
    expect(message.id).toBeTruthy();
  });

  it('形式違反のメールアドレスは送信せず形式の文言を出すこと（noValidate で原生 type=email は執行されない）', async () => {
    render(<LoginForm />);

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'not-an-email' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(await screen.findByText('メールアドレスの形式が正しくありません')).toBeInTheDocument();
    expect(mockedAuthApi.login).not.toHaveBeenCalled();
  });
});
