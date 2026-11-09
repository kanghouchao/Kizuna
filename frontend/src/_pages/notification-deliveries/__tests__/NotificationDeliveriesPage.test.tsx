import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { renderToString } from 'react-dom/server';
import {
  GroupNotificationDeliveriesPage,
  StoreNotificationDeliveriesPage,
} from '../ui/NotificationDeliveriesPage';
import { Delivery, notificationApi } from '@/entities/notification-delivery';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { notify } from '@/shared/notify';
import { storeSessionToken } from '@/shared/api';
import { setCurrentStore } from '@/shared/lib';
let mockStoreId = '1';
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: mockStoreId,
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));
jest.mock('@/entities/notification-delivery', () => ({
  notificationApi: {
    list: jest.fn(),
    get: jest.fn(),
    create: jest.fn(),
    queue: jest.fn(),
    retry: jest.fn(),
    resolve: jest.fn(),
    history: jest.fn(),
  },
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const api = jest.mocked(notificationApi);
const row: Delivery = {
  id: '10',
  source_type: 'ORDER',
  source_id: '00000000-0000-7000-8000-000000000123',
  channel: 'EMAIL',
  purpose: 'BUSINESS',
  subject: '予約の確認',
  body: '非公開の本文',
  scheduled_at: '2026-10-07T01:00:00Z',
  dedupe_key: 'draft-one',
  status: 'DRAFT',
  attempt_count: 0,
  created_at: '2026-10-07T00:00:00Z',
  version: 1,
  contact_decision: 'ALLOWED',
  transport_availability: 'AVAILABLE',
  scheduling_availability: 'MANUAL_TASK_ONLY',
};
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const permission = (...permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions,
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
beforeEach(() => {
  jest.resetAllMocks();
  mockStoreId = '1';
  // 結果不明の操作は、このタブの本人と現在店舗の作用域でだけ起こせる
  storeSessionToken({ token: 't', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
  setCurrentStore('1');
  permission('NOTIFICATION_VIEW', 'NOTIFICATION_MANAGE', 'NOTIFICATION_SEND');
  api.list.mockResolvedValue({ rows: [row], page: 0, pageCount: 1, total: 1 });
  api.get.mockResolvedValue(row);
  api.history.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
});
async function detail() {
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  return screen.findByRole('dialog');
}
test('閲覧権限がなければ通知を取得しない', () => {
  permission('NOTIFICATION_SEND');
  render(<StoreNotificationDeliveriesPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('閲覧権限がありません');
  expect(api.list).not.toHaveBeenCalled();
});
test('閲覧のみの担当者は本文を確認できるが作成や送信はできない', async () => {
  permission('NOTIFICATION_VIEW');
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  expect(await within(dialog).findByText('非公開の本文')).toBeVisible();
  expect(screen.queryByRole('button', { name: '通知を作成' })).not.toBeInTheDocument();
  expect(
    within(dialog).queryByRole('button', { name: '確認して送信待ちにする' })
  ).not.toBeInTheDocument();
});
test('結果不明では再試行を出さず未設定を成功として表示しない', async () => {
  api.get.mockResolvedValue({ ...row, status: 'UNKNOWN', transport_availability: 'UNAVAILABLE' });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  expect(await within(dialog).findByText(/送信結果が不明です/)).toBeVisible();
  expect(within(dialog).getByText(/メールの送信設定が利用できません/)).toBeVisible();
  expect(
    within(dialog).queryByRole('button', { name: '明示的に再試行する' })
  ).not.toBeInTheDocument();
  expect(within(dialog).getByRole('button', { name: '判定を記録する' })).toBeVisible();
});
test('送信権限が無ければ結果不明の判定を出さない', async () => {
  permission('NOTIFICATION_VIEW', 'NOTIFICATION_MANAGE');
  api.get.mockResolvedValue({ ...row, status: 'UNKNOWN' });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  expect(await within(dialog).findByText(/送信結果が不明です/)).toBeVisible();
  expect(within(dialog).queryByRole('button', { name: '判定を記録する' })).not.toBeInTheDocument();
});
test('判定と理由が無ければ送らず、未送達と判定すると再試行の入力に切り替わる', async () => {
  api.get.mockResolvedValue({ ...row, status: 'UNKNOWN', attempt_count: 1, version: 4 });
  api.resolve.mockResolvedValue({ ...row, status: 'FAILED', attempt_count: 2, version: 5 });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  fireEvent.click(await within(dialog).findByRole('button', { name: '判定を記録する' }));
  expect(await within(dialog).findByText('判定を選択してください')).toBeVisible();
  expect(within(dialog).getByText('判定の理由を入力してください')).toBeVisible();
  expect(api.resolve).not.toHaveBeenCalled();
  fireEvent.click(within(dialog).getByRole('radio', { name: /未送達/ }));
  fireEvent.change(within(dialog).getByLabelText('確認した内容・判定の理由'), {
    target: { value: '提供元の管理画面で未配信を確認' },
  });
  fireEvent.click(within(dialog).getByRole('button', { name: '判定を記録する' }));
  await waitFor(() =>
    expect(api.resolve).toHaveBeenCalledWith('10', 4, 'FAILED', '提供元の管理画面で未配信を確認')
  );
  expect(await within(dialog).findByRole('button', { name: '明示的に再試行する' })).toBeVisible();
  expect(within(dialog).queryByRole('button', { name: '判定を記録する' })).not.toBeInTheDocument();
  expect(notify.success).toHaveBeenCalledWith('判定を記録しました');
  await waitFor(() => expect(api.history).toHaveBeenCalledTimes(2));
});
test('判定が競合したら最新の状態を取るまで送らない', async () => {
  api.get
    .mockResolvedValueOnce({ ...row, status: 'UNKNOWN', version: 4 })
    .mockResolvedValueOnce({ ...row, status: 'SENT', version: 5 });
  api.resolve.mockRejectedValue({
    response: { status: 409, data: { error: '状態が変わりました' } },
  });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  fireEvent.click(await within(dialog).findByRole('radio', { name: /送達済み/ }));
  fireEvent.change(within(dialog).getByLabelText('確認した内容・判定の理由'), {
    target: { value: '配信を確認' },
  });
  fireEvent.click(within(dialog).getByRole('button', { name: '判定を記録する' }));
  const conflict = await within(dialog).findByText(/通知の状態が変わりました/);
  expect(conflict).toBeVisible();
  fireEvent.click(within(dialog).getByRole('button', { name: '判定を記録する' }));
  expect(api.resolve).toHaveBeenCalledTimes(1);
  fireEvent.click(
    within(dialog).getByRole('button', { name: '最新の状態を取得して判定をやり直す' })
  );
  await waitFor(() =>
    expect(within(dialog).queryByRole('button', { name: '判定を記録する' })).not.toBeInTheDocument()
  );
  expect(api.get).toHaveBeenCalledTimes(2);
});
test('試行履歴は職員の判定を提供元の結果と区別して示す', async () => {
  api.get.mockResolvedValue({ ...row, status: 'FAILED', attempt_count: 2 });
  api.history.mockResolvedValue({
    rows: [
      {
        id: 'a2',
        attempt_number: 2,
        status: 'FAILED',
        failure_code: 'MANUAL_RESOLUTION',
        started_at: '2026-10-07T02:00:00Z',
        finished_at: '2026-10-07T02:00:00Z',
        reason: '提供元で未配信を確認',
      },
      {
        id: 'a1',
        attempt_number: 1,
        status: 'UNKNOWN',
        failure_code: 'UNKNOWN',
        started_at: '2026-10-07T01:00:00Z',
        task_execution_id: 3,
        reason: '内容確認',
      },
    ],
    page: 0,
    pageCount: 1,
    total: 2,
  });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  const judged = (await within(dialog).findByText('提供元で未配信を確認')).closest('li')!;
  expect(within(judged).getByText('未送達と判定')).toBeVisible();
  expect(within(judged).getByText(/職員の判定/)).toBeVisible();
  expect(judged).not.toHaveTextContent('実行 #');
  const provider = within(dialog).getByText('内容確認').closest('li')!;
  expect(provider).toHaveTextContent('実行 #3');
  expect(within(provider).getByText('結果不明')).toBeVisible();
});
test('操作失敗後に理由を保持し連打で二重登録しない', async () => {
  let reject!: (e: Error) => void;
  api.queue.mockReturnValueOnce(
    new Promise((_, r) => {
      reject = r;
    })
  );
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  const input = await within(dialog).findByLabelText('内容を確認した理由・再試行の理由');
  fireEvent.change(input, { target: { value: '内容確認済み' } });
  const button = within(dialog).getByRole('button', { name: '確認して送信待ちにする' });
  fireEvent.click(button);
  fireEvent.click(button);
  await waitFor(() => expect(api.queue).toHaveBeenCalledTimes(1));
  await act(async () => reject(new Error('network')));
  expect(input).toHaveValue('内容確認済み');
  expect(notify.error).toHaveBeenCalledTimes(1);
  api.queue.mockResolvedValue({ ...row, status: 'QUEUED', version: 2 });
  fireEvent.click(button);
  await waitFor(() => expect(api.queue).toHaveBeenCalledTimes(2));
  expect(api.queue).toHaveBeenLastCalledWith('10', 1, '内容確認済み');
});
test('店舗変更後に旧店舗の遅い応答を表示しない', async () => {
  let resolve!: (data: {
    rows: Delivery[];
    page: number;
    pageCount: number;
    total: number;
  }) => void;
  api.list
    .mockReturnValueOnce(
      new Promise(r => {
        resolve = r;
      })
    )
    .mockResolvedValueOnce({ rows: [], page: 0, pageCount: 0, total: 0 });
  const view = render(<StoreNotificationDeliveriesPage />);
  mockStoreId = '2';
  view.rerender(<StoreNotificationDeliveriesPage />);
  expect(await screen.findByText('業務通知はありません')).toBeVisible();
  await act(async () => resolve({ rows: [row], page: 0, pageCount: 1, total: 1 }));
  expect(screen.queryByRole('button', { name: '内容・履歴' })).not.toBeInTheDocument();
});
test('詳細取得の失敗からその場で回復し404は一覧へ戻れる', async () => {
  api.get
    .mockRejectedValueOnce(new Error('network'))
    .mockRejectedValueOnce({ response: { status: 404 } });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  const failure = await within(dialog).findByRole('alert');
  fireEvent.click(within(failure).getByRole('button'));
  expect(await within(dialog).findByRole('button', { name: '一覧へ戻る' })).toBeVisible();
});
async function fillDraft() {
  fireEvent.click(screen.getByRole('button', { name: '通知を作成' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('受注・ゲスト申請ID'), {
    target: { value: '00000000-0000-7000-8000-000000000123' },
  });
  fireEvent.change(within(dialog).getByLabelText('件名'), { target: { value: '確認' } });
  fireEvent.change(within(dialog).getByLabelText('本文'), { target: { value: '本文' } });
  fireEvent.change(within(dialog).getByLabelText('送信予定（この端末の時刻）'), {
    target: { value: '2026-10-07T10:00' },
  });
  return dialog;
}
test('確定した入力エラーは内容を修正して再提出できる', async () => {
  api.create.mockRejectedValueOnce({
    response: { status: 400, data: { error: '起点を確認してください' } },
  });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await fillDraft();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを作成' }));
  await waitFor(() => expect(notify.error).toHaveBeenCalled());
  expect(within(dialog).getByLabelText('受注・ゲスト申請ID')).not.toBeDisabled();
});

test('作成結果が不明なダイアログを閉じても同じ要求を再確認できる', async () => {
  api.create.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(row);
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await fillDraft();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを作成' }));
  await within(dialog).findByRole('button', { name: '同じ要求で結果を確認' });
  fireEvent.keyDown(dialog, { key: 'Escape', code: 'Escape' });
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: '通知を作成' }));
  const reopened = await screen.findByRole('dialog');
  expect(within(reopened).getByLabelText('送信予定（この端末の時刻）')).toHaveValue(
    '2026-10-07T10:00'
  );
  expect(within(reopened).getByLabelText('件名')).toBeDisabled();
  fireEvent.click(within(reopened).getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(notify.success).toHaveBeenCalledWith('通知の下書きを作成しました'));
  expect(api.create.mock.calls[0][2]).toEqual({ headers: { 'X-Store-ID': '1' } });
});

test.each([400, 404, 409])(
  '結果不明の再確認が業務拒否 %s を受けたら不明を解き、入力を直して送り直せる',
  async status => {
    api.create
      .mockRejectedValueOnce(new Error('network'))
      .mockRejectedValueOnce({ response: { status } });
    render(<StoreNotificationDeliveriesPage />);
    const dialog = await fillDraft();
    fireEvent.click(within(dialog).getByRole('button', { name: '下書きを作成' }));
    fireEvent.click(await within(dialog).findByRole('button', { name: '同じ要求で結果を確認' }));
    expect(await within(dialog).findByRole('button', { name: '下書きを作成' })).toBeEnabled();
    expect(within(dialog).getByLabelText('件名')).toBeEnabled();
    expect(within(dialog).queryByText(/作成結果が確認できません/)).not.toBeInTheDocument();
  }
);

test('本人情報が未取得のサーバー描画では権限に依存した本文を返さない', () => {
  mockedUseMe.mockReturnValue({
    me: null,
    isLoading: true,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
  const html = renderToString(<StoreNotificationDeliveriesPage />);
  expect(html).toContain('読み込み中');
  expect(html).not.toContain('業務通知はありません');
  expect(api.list).not.toHaveBeenCalled();
});

test('確認操作の404では保存を外して一覧へ戻れる', async () => {
  api.queue.mockRejectedValue({ response: { status: 404 } });
  render(<StoreNotificationDeliveriesPage />);
  const dialog = await detail();
  fireEvent.change(await within(dialog).findByLabelText('内容を確認した理由・再試行の理由'), {
    target: { value: '内容確認' },
  });
  fireEvent.click(within(dialog).getByRole('button', { name: '確認して送信待ちにする' }));
  expect(await within(dialog).findByRole('button', { name: '一覧へ戻る' })).toBeVisible();
  expect(
    within(dialog).queryByRole('button', { name: '確認して送信待ちにする' })
  ).not.toBeInTheDocument();
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
});
test('通知一覧はページ番号で送り、次のページを取り直す', async () => {
  api.list
    .mockResolvedValueOnce({ rows: [row], page: 0, pageCount: 2, total: 51 })
    .mockResolvedValueOnce({
      rows: [{ ...row, id: '102', source_id: '456' }],
      page: 1,
      pageCount: 2,
      total: 51,
    });
  render(<StoreNotificationDeliveriesPage />);
  expect(await screen.findByText(/123/)).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  expect(await screen.findByText(/456/)).toBeInTheDocument();
  expect(api.list).toHaveBeenLastCalledWith(1);
});
test('送信試行はページ番号で送り、次のページを取り直す', async () => {
  const attempt = (id: string, reason: string) => ({
    id,
    attempt_number: 1,
    status: 'FAILED' as const,
    started_at: '2026-10-07T01:00:00Z',
    task_execution_id: 1,
    reason,
    failure_code: undefined,
  });
  api.history
    .mockResolvedValueOnce({
      rows: [attempt('a1', '一頁目の試行')],
      page: 0,
      pageCount: 2,
      total: 51,
    })
    .mockResolvedValueOnce({
      rows: [attempt('a2', '二頁目の試行')],
      page: 1,
      pageCount: 2,
      total: 51,
    });
  render(<StoreNotificationDeliveriesPage />);
  const modal = await detail();
  expect(await within(modal).findByText('一頁目の試行')).toBeInTheDocument();
  fireEvent.click(within(modal).getAllByRole('button', { name: '次へ' })[0]);
  expect(await within(modal).findByText('二頁目の試行')).toBeInTheDocument();
  expect(api.history).toHaveBeenLastCalledWith('10', 1);
});
describe('グループのコンテキスト', () => {
  const member: Delivery = { ...row, id: '20', source_type: 'MEMBER', source_id: '55' };
  beforeEach(() => {
    api.list.mockResolvedValue({ rows: [member], page: 0, pageCount: 1, total: 1 });
  });
  test('会員宛の通知を一覧し、管理権限があっても作成口は出さない', async () => {
    render(<GroupNotificationDeliveriesPage />);
    expect(await screen.findByRole('row', { name: /会員\s*55/ })).toBeVisible();
    expect(screen.getByText('会員宛の業務メールと送信履歴')).toBeVisible();
    expect(screen.queryByRole('button', { name: '通知を作成' })).not.toBeInTheDocument();
  });
  test('送信不可の通知は理由を添えて再試行できる', async () => {
    api.get.mockResolvedValue({ ...member, status: 'BLOCKED', contact_decision: 'NOT_ALLOWED' });
    api.retry.mockResolvedValue({ ...member, status: 'QUEUED', version: 2 });
    render(<GroupNotificationDeliveriesPage />);
    const dialog = await detail();
    expect(await within(dialog).findByText('会員 #55')).toBeVisible();
    fireEvent.change(within(dialog).getByLabelText('内容を確認した理由・再試行の理由'), {
      target: { value: '本人が許可した' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '明示的に再試行する' }));
    await waitFor(() => expect(api.retry).toHaveBeenCalledWith('20', 1, '本人が許可した'));
  });
  test('結果不明の通知は送信権限があれば判定できる', async () => {
    api.get.mockResolvedValue({ ...member, status: 'UNKNOWN' });
    render(<GroupNotificationDeliveriesPage />);
    const dialog = await detail();
    expect(await within(dialog).findByRole('button', { name: '判定を記録する' })).toBeVisible();
  });
});
