import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { notify } from '@/shared/notify';
import PlatformLoginForm from '../PlatformLoginForm';
import { platformAuthApi } from '@/entities/user';
import type { PlatformMeResponse } from '@/entities/user';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
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
const mockedNotifyError = notify.error as jest.Mock;

function meResponse(overrides: Partial<PlatformMeResponse>): PlatformMeResponse {
  return {
    email: 'user@kizuna.test',
    display_name: '本人',
    user_type: 'STAFF',
    permissions: [],
    console: 'none',
    store_bridge: false,
    store_scope_type: 'ALL_STORES',
    store_ids: [],
    line_linked: false,
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
    expect(mockedNotifyError).not.toHaveBeenCalled();
  });

  it('会員が公式サイトの予約導線から弾かれていた場合、その画面へ戻ること', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER', console: 'none' }));
    Cookies.set('member-return-path', '/member/reservations/new?store=store1.kizuna.test');

    await submitLoginForm();

    await waitFor(() =>
      expect(mockPush).toHaveBeenCalledWith('/member/reservations/new?store=store1.kizuna.test')
    );
    expect(Cookies.get('member-return-path')).toBeUndefined();
  });

  it('伝票の QR から来ていた場合、トークン（フラグメント）ごと申領画面へ戻ること', async () => {
    // フラグメントはサーバへ送られないため cookie には無い。申領に要るトークンはこれしか無く、
    // 落とすと戻れても申領できない画面に着地する
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER', console: 'none' }));
    Cookies.set('member-return-path', '/member/receipts');
    sessionStorage.setItem('member-return-fragment', '#tok3n');

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/receipts#tok3n'));
    expect(sessionStorage.getItem('member-return-fragment')).toBeNull();
  });

  it('会員以外がログインしたら、預かっていた戻り先とトークンを捨てること', async () => {
    // 同じタブで後からログインした会員がその戻り先へ運ばれると、申領画面は開いた時点で
    // 取り込むため「他人の伝票を無確認で申領する」経路になる
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'CAST', console: 'none' }));
    Cookies.set('member-return-path', '/member/receipts');
    sessionStorage.setItem('member-return-fragment', '#tok3n');

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/cast/schedule/'));
    expect(Cookies.get('member-return-path')).toBeUndefined();
    expect(sessionStorage.getItem('member-return-fragment')).toBeNull();
  });

  it('戻り先に外部 URL が仕込まれていても既定のホームへ遷移すること', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER', console: 'none' }));
    Cookies.set('member-return-path', 'https://evil.test/');

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/'));
  });

  it('user_type=MEMBER は member セッションを開始し /member/ へ遷移する', async () => {
    mockedAuthApi.me.mockResolvedValue(meResponse({ user_type: 'MEMBER', console: 'none' }));

    await submitLoginForm();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/'));
    expect(Cookies.get('platform-role')).toBe('member');
    expect(Cookies.get('token')).toBe('jwt-token');
    expect(mockedNotifyError).not.toHaveBeenCalled();
  });
});

describe('PlatformLoginForm のクライアント検証（#598）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('未入力のまま押すと欄ごとの文言が出て、送信は行われないこと', async () => {
    render(<PlatformLoginForm />);

    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(await screen.findByText('メールアドレスを入力してください')).toBeInTheDocument();
    expect(screen.getByText('パスワードを入力してください')).toBeInTheDocument();
    expect(mockedAuthApi.login).not.toHaveBeenCalled();
    // 検証を理由にボタンを塞がない（押させて文言を出す）
    expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled();
  });

  it('文言が欄と結び付いていること（読み上げ環境で「どの欄が」まで届く）', async () => {
    render(<PlatformLoginForm />);

    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    const message = await screen.findByText('メールアドレスを入力してください');
    const input = screen.getByLabelText('メールアドレス');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-describedby', message.id);
    expect(message.id).toBeTruthy();
  });

  it('形式違反のメールアドレスは送信せず形式の文言を出すこと（noValidate で原生 type=email は執行されない）', async () => {
    render(<PlatformLoginForm />);

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'not-an-email' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(await screen.findByText('メールアドレスの形式が正しくありません')).toBeInTheDocument();
    expect(mockedAuthApi.login).not.toHaveBeenCalled();
  });
});
