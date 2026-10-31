import { act, fireEvent, render, screen } from '@testing-library/react';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';

const redirectMock = jest.fn();
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  redirectToLogin: (reason?: string) => redirectMock(reason),
}));

import {
  apiClient,
  clearSessionToken,
  getSessionUserId,
  setSessionAlert,
  storeSessionToken,
} from '@/shared/api';
import { takeMemberReturnPath } from '@/shared/lib';
import { SessionGate } from '../SessionGate';

/** 同じブラウザのタブ間の通知を、テストの中だけで配る代役。送信元には届かない点も本物に合わせる。 */
class FakeBroadcastChannel {
  static open: FakeBroadcastChannel[] = [];
  onmessage: ((event: MessageEvent) => void) | null = null;
  constructor(readonly name: string) {
    FakeBroadcastChannel.open.push(this);
  }
  postMessage(data: unknown) {
    FakeBroadcastChannel.open
      .filter(other => other !== this && other.name === this.name)
      .forEach(other => other.onmessage?.({ data } as MessageEvent));
  }
  close() {
    FakeBroadcastChannel.open = FakeBroadcastChannel.open.filter(c => c !== this);
  }
}

function broadcastFromAnotherTab(data: unknown) {
  const sender = new FakeBroadcastChannel('kizuna-logout');
  act(() => sender.postMessage(data));
  sender.close();
}

type Reply = { status: number; data?: unknown; headers?: Record<string, string> };

function serve(routes: Record<string, Reply[]>) {
  const sent: string[] = [];
  apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => {
    sent.push(config.url ?? '');
    const reply = routes[config.url ?? '']?.shift() ?? { status: 500 };
    const response = {
      data: reply.data ?? {},
      status: reply.status,
      statusText: '',
      headers: reply.headers ?? {},
      config,
    };
    if (reply.status >= 400) throw Object.assign(new Error('HTTP'), { response, config });
    return response;
  }) as AxiosAdapter;
  return sent;
}

const issued = (userId: number) => ({
  status: 200,
  data: { token: `t${userId}`, expires_at: 1, user_id: userId, refresh_expires_at: 2 },
});

describe('SessionGate', () => {
  const originalAdapter = apiClient.defaults.adapter;
  const originalChannel = globalThis.BroadcastChannel;

  beforeEach(() => {
    jest.clearAllMocks();
    clearSessionToken();
    setSessionAlert(null);
    FakeBroadcastChannel.open = [];
    globalThis.BroadcastChannel = FakeBroadcastChannel as unknown as typeof BroadcastChannel;
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    globalThis.BroadcastChannel = originalChannel;
  });

  it('開いたときにリフレッシュを 1 回呼び、トークンを得てから中身を描く', async () => {
    const sent = serve({ '/auth/refresh': [issued(7)] });

    render(<SessionGate>中身</SessionGate>);

    expect(screen.queryByText('中身')).not.toBeInTheDocument();
    expect(await screen.findByText('中身')).toBeInTheDocument();
    expect(sent).toEqual(['/auth/refresh']);
    expect(getSessionUserId()).toBe(7);
  });

  it('資格情報が変わっていれば再ログインを促し、ボタンを押すまでログインへ移らない', async () => {
    serve({
      '/auth/refresh': [{ status: 401, headers: { 'x-auth-failure': 'credential_changed' } }],
    });

    render(<SessionGate>中身</SessionGate>);

    expect(
      await screen.findByText('権限やパスワードが変更されました。もう一度ログインしてください')
    ).toBeInTheDocument();
    expect(redirectMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'ログイン画面へ' }));

    expect(redirectMock).toHaveBeenCalled();
  });

  it('再ログインのボタンは、会員ポータルの現在地をフラグメントごと戻り先に預けてからログインへ移る', async () => {
    window.history.pushState({}, '', '/member/receipts#tok3n');
    serve({
      '/auth/refresh': [{ status: 401, headers: { 'x-auth-failure': 'credential_changed' } }],
    });
    render(<SessionGate>中身</SessionGate>);

    fireEvent.click(await screen.findByRole('button', { name: 'ログイン画面へ' }));

    expect(takeMemberReturnPath()).toBe('/member/receipts#tok3n');
    window.history.pushState({}, '', '/');
  });

  it('操作中のリフレッシュが 409 を受けると再読込を促すダイアログを出し、自動では再読込しない', async () => {
    serve({
      '/auth/refresh': [issued(7), { status: 409, data: { error: 'mismatch' } }],
      '/orders': [{ status: 401 }],
    });
    render(
      <SessionGate>
        <input aria-label="メモ" defaultValue="入力中" />
      </SessionGate>
    );
    await screen.findByLabelText('メモ');

    await act(async () => {
      await apiClient.get('/orders').catch(() => undefined);
    });

    expect(await screen.findByText('別のアカウントでログインされています')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));
    // 閉じれば入力中の内容はそのまま残る
    expect(screen.getByLabelText('メモ')).toHaveValue('入力中');
  });

  it('同じ利用者の他のタブがログアウトすると、トークンを消してログインへ移る', async () => {
    serve({ '/auth/refresh': [issued(7)] });
    render(<SessionGate>中身</SessionGate>);
    await screen.findByText('中身');

    broadcastFromAnotherTab({ user_id: 7 });

    expect(getSessionUserId()).toBeUndefined();
    expect(redirectMock).toHaveBeenCalled();
  });

  it('別の利用者のログアウト通知では、画面もトークンも変わらない', async () => {
    serve({ '/auth/refresh': [issued(7)] });
    render(<SessionGate>中身</SessionGate>);
    await screen.findByText('中身');

    broadcastFromAnotherTab({ user_id: 8 });

    expect(getSessionUserId()).toBe(7);
    expect(redirectMock).not.toHaveBeenCalled();
    expect(screen.getByText('中身')).toBeInTheDocument();
  });
});
