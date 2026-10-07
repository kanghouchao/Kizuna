import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import TaskExecutionsPage from '@/_pages/platform-task-executions';
import AuditEventsPage from '@/_pages/platform-audit-events';
import { taskExecutionApi, ExecutionSummary } from '@/entities/task-execution';
import { auditEventApi } from '@/entities/audit-event';
import { notify } from '@/shared/notify';
jest.mock('@/entities/task-execution', () => ({
  taskExecutionApi: {
    list: jest.fn(),
    get: jest.fn(),
    candidates: jest.fn(),
    retry: jest.fn(),
    interrupt: jest.fn(),
    create: jest.fn(),
  },
}));
jest.mock('@/entities/audit-event', () => ({ auditEventApi: { list: jest.fn(), get: jest.fn() } }));
jest.mock('@/shared/notify', () => ({ notify: { error: jest.fn(), success: jest.fn() } }));
const tasks = jest.mocked(taskExecutionApi);
const audit = jest.mocked(auditEventApi);
const row: ExecutionSummary = {
  id: 1,
  request_id: 1,
  attempt_number: 1,
  task_name: 'SERVICE_IDENTITY_CHECK',
  service_user_id: 10,
  service_name: '確認処理',
  store_id: null,
  store_name: null,
  period_start: '2026-10-07',
  period_end: '2026-10-07',
  origin: 'SCHEDULED',
  status: 'FAILED',
  started_at: '2026-10-07T01:00:00Z',
  finished_at: '2026-10-07T01:00:01Z',
  processed_count: null,
  failure_code: 'EXECUTION_FAILED',
};
beforeEach(() => {
  jest.resetAllMocks();
  tasks.candidates.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
});
test('実行要求の応答を受け取れなくても同じ入力の再送には同じ実行キーを使う', async () => {
  tasks.list.mockResolvedValue({ rows: [], nextCursor: null });
  tasks.candidates.mockResolvedValue({
    rows: [{ id: 10, display_name: '確認処理' }],
    page: 0,
    pageCount: 1,
    total: 1,
  });
  const result = {
    execution: { ...row, status: 'SUCCEEDED' as const, processed_count: 0 },
    logical_key: 'recorded',
    retry_of: null,
    initiated_by: 20,
    reason: '初回実行',
  };
  tasks.create.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(result);
  tasks.get.mockResolvedValue(result);
  render(<TaskExecutionsPage />);
  fireEvent.click(await screen.findByRole('combobox', { name: '実行主体' }));
  const option = await screen.findByRole('option', { name: '確認処理' });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
  fireEvent.change(screen.getByLabelText('対象日'), { target: { value: '2026-10-07' } });
  fireEvent.click(screen.getByRole('button', { name: '実行確認を記録' }));
  await waitFor(() => expect(notify.error).toHaveBeenCalledTimes(1));
  const first = tasks.create.mock.calls[0][0];
  expect(first.logical_key).toMatch(/^[a-f0-9]{32}$/);
  fireEvent.click(screen.getByRole('button', { name: '実行確認を記録' }));
  const modal = await screen.findByRole('dialog');
  expect(await within(modal).findByText('成功', { exact: true })).toBeVisible();
  expect(tasks.create).toHaveBeenCalledTimes(2);
  expect(tasks.create.mock.calls[1][0]).toEqual(first);
});
test('履歴取得の失敗を空一覧と区別し再取得できる', async () => {
  tasks.list
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ rows: [], nextCursor: null });
  render(<TaskExecutionsPage />);
  const failure = await screen.findByRole('alert');
  expect(failure).toHaveTextContent('実行履歴を取得できませんでした');
  expect(screen.queryByText('実行履歴がありません')).not.toBeInTheDocument();
  fireEvent.click(within(failure).getByRole('button'));
  expect(await screen.findByText('実行履歴がありません')).toBeVisible();
});
test('再試行の競合では理由を保持し成功するまで履歴を変更しない', async () => {
  tasks.list.mockResolvedValue({ rows: [row], nextCursor: null });
  tasks.get.mockResolvedValue({
    execution: row,
    logical_key: 'daily',
    retry_of: null,
    initiated_by: null,
    reason: '初回実行',
  });
  tasks.retry.mockRejectedValueOnce(new Error('conflict')).mockResolvedValueOnce({
    execution: { ...row, id: 2, attempt_number: 2, status: 'SUCCEEDED', processed_count: 0 },
    logical_key: 'daily',
    retry_of: 1,
    initiated_by: 20,
    reason: '修正したため',
  });
  render(<TaskExecutionsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '詳細' }));
  const modal = screen.getByRole('dialog');
  const reason = await within(modal).findByLabelText('操作の理由');
  fireEvent.change(reason, { target: { value: '修正したため' } });
  fireEvent.click(within(modal).getByRole('button', { name: '再試行する' }));
  await waitFor(() => expect(notify.error).toHaveBeenCalled());
  expect(reason).toHaveValue('修正したため');
  expect(within(modal).getByText('失敗', { exact: true })).toBeVisible();
  tasks.get.mockResolvedValue({
    execution: { ...row, id: 2, attempt_number: 2, status: 'SUCCEEDED', processed_count: 0 },
    logical_key: 'daily',
    retry_of: 1,
    initiated_by: 20,
    reason: '修正したため',
  });
  fireEvent.click(within(modal).getByRole('button', { name: '再試行する' }));
  expect(await within(modal).findByText('成功', { exact: true })).toBeVisible();
  expect(within(modal).queryByRole('button', { name: '再試行する' })).not.toBeInTheDocument();
  expect(tasks.retry).toHaveBeenLastCalledWith(1, '修正したため');
  const beforeRefresh = tasks.get.mock.calls.length;
  fireEvent.click(within(modal).getByRole('button', { name: '最新の状態を取得' }));
  await waitFor(() => expect(tasks.get).toHaveBeenCalledTimes(beforeRefresh + 1));
  expect(tasks.get).toHaveBeenLastCalledWith(2);
});
test('監査詳細の取得失敗は一覧の値で埋めず領域内で再取得する', async () => {
  const event = {
    id: 1,
    occurred_at: '2026-10-07T01:00:00Z',
    actor_id: 3,
    actor_type: 'STAFF',
    actor_name: '担当',
    store_id: null,
    action: 'ROLE_CHANGED',
    result: 'SUCCEEDED',
    target_type: 'ROLE',
    target_id: '5',
    source_type: null,
    source_id: null,
  };
  audit.list.mockResolvedValue({ rows: [event], nextCursor: null });
  audit.get.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
    event,
    before_values: { role_ids: '1' },
    after_values: { role_ids: '2' },
  });
  render(<AuditEventsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '詳細' }));
  const modal = screen.getByRole('dialog');
  const error = await within(modal).findByRole('alert');
  expect(within(modal).queryByText('変更前')).not.toBeInTheDocument();
  fireEvent.click(within(error).getByRole('button'));
  expect(await within(modal).findByRole('cell', { name: '2' })).toBeVisible();
});
