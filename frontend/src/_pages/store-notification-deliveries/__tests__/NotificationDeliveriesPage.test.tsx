import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { renderToString } from 'react-dom/server';
import { NotificationDeliveriesPage } from '../ui/NotificationDeliveriesPage';
import { Delivery, notificationApi } from '@/entities/notification-delivery';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { notify } from '@/shared/notify';
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
  source_id: '123',
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
    me: { email: 'staff@example.com', user_type: 'STAFF', permissions, line_linked: false },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
beforeEach(() => {
  jest.resetAllMocks();
  mockStoreId = '1';
  Object.defineProperty(globalThis.crypto, 'randomUUID', { configurable: true, value: undefined });
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
  render(<NotificationDeliveriesPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('閲覧権限がありません');
  expect(api.list).not.toHaveBeenCalled();
});
test('閲覧のみの担当者は本文を確認できるが作成や送信はできない', async () => {
  permission('NOTIFICATION_VIEW');
  render(<NotificationDeliveriesPage />);
  const dialog = await detail();
  expect(await within(dialog).findByText('非公開の本文')).toBeVisible();
  expect(screen.queryByRole('button', { name: '通知を作成' })).not.toBeInTheDocument();
  expect(
    within(dialog).queryByRole('button', { name: '確認して送信待ちにする' })
  ).not.toBeInTheDocument();
});
test('結果不明では再試行を出さず未設定を成功として表示しない', async () => {
  api.get.mockResolvedValue({ ...row, status: 'UNKNOWN', transport_availability: 'UNAVAILABLE' });
  render(<NotificationDeliveriesPage />);
  const dialog = await detail();
  expect(await within(dialog).findByText(/送信結果が不明です/)).toBeVisible();
  expect(within(dialog).getByText(/メールの送信設定が利用できません/)).toBeVisible();
  expect(
    within(dialog).queryByRole('button', { name: '明示的に再試行する' })
  ).not.toBeInTheDocument();
});
test('操作失敗後に理由を保持し連打で二重登録しない', async () => {
  let reject!: (e: Error) => void;
  api.queue.mockReturnValueOnce(
    new Promise((_, r) => {
      reject = r;
    })
  );
  render(<NotificationDeliveriesPage />);
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
  const view = render(<NotificationDeliveriesPage />);
  mockStoreId = '2';
  view.rerender(<NotificationDeliveriesPage />);
  expect(await screen.findByText('業務通知はありません')).toBeVisible();
  await act(async () => resolve({ rows: [row], page: 0, pageCount: 1, total: 1 }));
  expect(screen.queryByRole('button', { name: '内容・履歴' })).not.toBeInTheDocument();
});
test('詳細取得の失敗からその場で回復し404は一覧へ戻れる', async () => {
  api.get
    .mockRejectedValueOnce(new Error('network'))
    .mockRejectedValueOnce({ response: { status: 404 } });
  render(<NotificationDeliveriesPage />);
  const dialog = await detail();
  const failure = await within(dialog).findByRole('alert');
  fireEvent.click(within(failure).getByRole('button'));
  expect(await within(dialog).findByRole('button', { name: '一覧へ戻る' })).toBeVisible();
});
async function fillDraft() {
  fireEvent.click(screen.getByRole('button', { name: '通知を作成' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('受注・ゲスト申請ID'), {
    target: { value: '123' },
  });
  fireEvent.change(within(dialog).getByLabelText('件名'), { target: { value: '確認' } });
  fireEvent.change(within(dialog).getByLabelText('本文'), { target: { value: '本文' } });
  fireEvent.change(within(dialog).getByLabelText('送信予定（この端末の時刻）'), {
    target: { value: '2026-10-07T10:00' },
  });
  return dialog;
}
test('作成応答が不明なら同じ内容と要求キーで再確認する', async () => {
  api.create.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(row);
  render(<NotificationDeliveriesPage />);
  const dialog = await fillDraft();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを作成' }));
  const retry = await within(dialog).findByRole('button', { name: '同じ要求で結果を確認' });
  expect(within(dialog).getByLabelText('件名')).toBeDisabled();
  fireEvent.click(retry);
  await waitFor(() => expect(api.create).toHaveBeenCalledTimes(2));
  expect(api.create.mock.calls[1][0]).toEqual(api.create.mock.calls[0][0]);
});
test('確定した入力エラーは内容を修正して再提出できる', async () => {
  api.create.mockRejectedValueOnce({
    response: { status: 400, data: { error: '起点を確認してください' } },
  });
  render(<NotificationDeliveriesPage />);
  const dialog = await fillDraft();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを作成' }));
  await waitFor(() => expect(notify.error).toHaveBeenCalled());
  expect(within(dialog).getByLabelText('受注・ゲスト申請ID')).not.toBeDisabled();
});

test('作成結果が不明なダイアログを閉じても同じ要求を再確認できる', async () => {
  api.create.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(row);
  render(<NotificationDeliveriesPage />);
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
  fireEvent.click(within(reopened).getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(api.create).toHaveBeenCalledTimes(2));
  expect(api.create.mock.calls[1][0]).toEqual(api.create.mock.calls[0][0]);
});

test('不明な作成結果の再照会が拒否されても元のキーを保持する', async () => {
  api.create
    .mockRejectedValueOnce(new Error('network'))
    .mockRejectedValueOnce({ response: { status: 403 } })
    .mockResolvedValueOnce(row);
  render(<NotificationDeliveriesPage />);
  const dialog = await fillDraft();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを作成' }));
  const retry = await within(dialog).findByRole('button', { name: '同じ要求で結果を確認' });
  fireEvent.click(retry);
  await waitFor(() => expect(notify.error).toHaveBeenCalledTimes(2));
  expect(within(dialog).getByLabelText('件名')).toBeDisabled();
  fireEvent.click(retry);
  await waitFor(() => expect(api.create).toHaveBeenCalledTimes(3));
  expect(api.create.mock.calls[2][0]).toEqual(api.create.mock.calls[0][0]);
});

test('本人情報が未取得のサーバー描画では権限に依存した本文を返さない', () => {
  mockedUseMe.mockReturnValue({
    me: null,
    isLoading: true,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
  const html = renderToString(<NotificationDeliveriesPage />);
  expect(html).toContain('読み込み中');
  expect(html).not.toContain('業務通知はありません');
  expect(api.list).not.toHaveBeenCalled();
});

test('確認操作の404では保存を外して一覧へ戻れる', async () => {
  api.queue.mockRejectedValue({ response: { status: 404 } });
  render(<NotificationDeliveriesPage />);
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
  render(<NotificationDeliveriesPage />);
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
  render(<NotificationDeliveriesPage />);
  const modal = await detail();
  expect(await within(modal).findByText('一頁目の試行')).toBeInTheDocument();
  fireEvent.click(within(modal).getAllByRole('button', { name: '次へ' })[0]);
  expect(await within(modal).findByText('二頁目の試行')).toBeInTheDocument();
  expect(api.history).toHaveBeenLastCalledWith('10', 1);
});
