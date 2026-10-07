import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ReviewsPage } from '../ui/ReviewsPage';
import { reviewApi, Review } from '@/entities/review';
import { notify } from '@/shared/notify';
import { readTokenClaims } from '@/shared/lib';
let mockStoreId = '1';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: mockStoreId }) }));
jest.mock('@/entities/review', () => ({
  reviewApi: { list: jest.fn(), get: jest.fn(), history: jest.fn(), write: jest.fn() },
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
const api = jest.mocked(reviewApi);
const claims = jest.mocked(readTokenClaims);
const row: Review = {
  id: '10',
  intake_source: 'STAFF_RECORDED',
  display_name: '表示名',
  received_via: 'PAPER',
  received_at: '2026-01-01T00:00:00Z',
  created_at: '2026-01-01T00:00:00Z',
  status: 'PENDING',
  permission_status: 'NOT_GRANTED',
  version: 0,
  body: '保護された本文',
  origin_order_id: null,
  origin_checked_at: null,
  origin_order_version: null,
  recorded_by: { id: '1', display_name: '受付担当' },
  supersedes_id: null,
  superseded_by_id: null,
  permission: null,
  publication_eligible: false,
  publication_blockers: ['NOT_APPROVED', 'NO_PERMISSION'],
  publication_connection: 'NOT_CONFIGURED',
};
const permission = (...names: string[]) =>
  claims.mockReturnValue({
    userType: 'STAFF',
    storeBridge: true,
    authorities: names.map(p => `PERM_${p}`),
  });
beforeEach(() => {
  jest.resetAllMocks();
  mockStoreId = '1';
  permission('REVIEW_VIEW');
  api.list.mockResolvedValue({ rows: [row], page: 0, pageCount: 1, total: 1 });
  api.get.mockResolvedValue(row);
  api.history.mockResolvedValue({ rows: [], nextCursor: null });
});
test('閲覧権限がなければ取得せず、閲覧のみは手動記録と未設定を確認できる', async () => {
  permission('REVIEW_MANAGE');
  const view = render(<ReviewsPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('閲覧権限がありません');
  expect(api.list).not.toHaveBeenCalled();
  permission('REVIEW_VIEW');
  view.rerender(<ReviewsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  const detail = await screen.findByRole('dialog');
  expect(await within(detail).findByText('保護された本文')).toBeVisible();
  expect(within(detail).getByText('スタッフによる記録')).toBeVisible();
  expect(within(detail).getByText('公開連携は未設定')).toBeVisible();
  expect(screen.queryByRole('button', { name: '口コミを受付' })).not.toBeInTheDocument();
  expect(within(detail).queryByRole('button', { name: '内部承認' })).not.toBeInTheDocument();
});

test('審査権限だけで内部承認し、理由付き確認を経て許可未取得のまま反映する', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write.mockResolvedValue({
    review: { ...row, status: 'APPROVED', version: 1, publication_blockers: ['NO_PERMISSION'] },
    operation: {
      id: '20',
      type: 'APPROVED',
      review_id: row.id,
      committed_version: 1,
      replayed: false,
    },
  });
  render(<ReviewsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  fireEvent.click(await screen.findByRole('button', { name: '内部承認' }));
  const editor = await screen.findByRole('dialog', { name: '内部承認' });
  expect(within(editor).getByText(/内部承認のみ/)).toBeVisible();
  fireEvent.change(within(editor).getByLabelText('判断・変更の理由'), {
    target: { value: '内容確認済み' },
  });
  fireEvent.click(within(editor).getByRole('button', { name: '確認へ' }));
  const confirmation = await screen.findByRole('alertdialog');
  fireEvent.click(within(confirmation).getByRole('button', { name: '確定する' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'DECIDE',
    id: '10',
    input: { version: 0, decision: 'APPROVE', reason: '内容確認済み' },
  });
  expect(
    await screen.findByText('内部承認済み', { selector: '[data-slot="badge"]' })
  ).toBeVisible();
  expect(screen.queryByRole('button', { name: '公開許可を記録' })).not.toBeInTheDocument();
});

test('受付の応答が不明でも画面を閉じて同じ要求を再確認し、二重操作を防ぐ', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.write.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
    review: row,
    operation: {
      id: '20',
      type: 'RECEIVED',
      review_id: row.id,
      committed_version: 0,
      replayed: true,
    },
  });
  render(<ReviewsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '口コミを受付' }));
  const editor = await screen.findByRole('dialog', { name: '口コミを受付' });
  fireEvent.change(within(editor).getByLabelText('口コミ本文'), {
    target: { value: '  原文の空白を保持  ' },
  });
  fireEvent.change(within(editor).getByLabelText('受け取った日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  fireEvent.click(within(editor).getByRole('button', { name: '受付を記録' }));
  fireEvent.click(within(editor).getByRole('button', { name: '受付を記録' }));
  await within(editor).findByText(/結果が確認できません/);
  fireEvent.keyDown(editor, { key: 'Escape', code: 'Escape' });
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(api.write).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: '口コミを受付' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
  expect(api.write.mock.calls[1][0]).toEqual(api.write.mock.calls[0][0]);
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'CREATE',
    input: { body: '  原文の空白を保持  ', origin_order_id: null },
  });
});
test('公開許可を独立して記録し、取り下げ後も許可の撤回操作を残す', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  const granted: Review = {
    ...row,
    status: 'APPROVED',
    permission_status: 'GRANTED',
    version: 2,
    publication_eligible: true,
    publication_blockers: [],
    permission: {
      id: '30',
      scope: 'OWN_STORE_WEBSITE',
      basis_type: 'WRITTEN',
      granted_at: '2026-01-01T00:00:00Z',
      evidence_note: '書面の記録',
      recorded_by: row.recorded_by,
      recorded_at: row.created_at,
      revocation: null,
    },
  };
  api.get.mockResolvedValue({ ...row, status: 'APPROVED', version: 1 });
  api.write.mockResolvedValueOnce({
    review: granted,
    operation: {
      id: '20',
      type: 'PERMISSION_GRANTED',
      review_id: row.id,
      committed_version: 2,
      replayed: false,
    },
  });
  render(<ReviewsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  fireEvent.click(await screen.findByRole('button', { name: '公開許可を記録' }));
  const editor = await screen.findByRole('dialog', { name: '公開許可を記録' });
  expect(within(editor).getByText(/自店舗ウェブサイト/)).toBeVisible();
  fireEvent.change(within(editor).getByLabelText('許可を受けた日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  fireEvent.change(within(editor).getByLabelText('許可を確認した根拠'), {
    target: { value: '書面の記録' },
  });
  fireEvent.click(within(editor).getByRole('button', { name: '確認へ' }));
  fireEvent.click(
    within(await screen.findByRole('alertdialog')).getByRole('button', { name: '確定する' })
  );
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'GRANT',
    input: { version: 1, evidence_note: '書面の記録', basis_type: 'WRITTEN' },
  });
  api.get.mockResolvedValue({
    ...granted,
    status: 'WITHDRAWN',
    version: 3,
    publication_eligible: false,
    publication_blockers: ['WITHDRAWN'],
  });
  fireEvent.click(await screen.findByRole('button', { name: '最新状態を確認' }));
  expect(await screen.findByRole('button', { name: '公開許可を撤回' })).toBeEnabled();
  await waitFor(() =>
    expect(screen.queryByRole('button', { name: '口コミを取り下げ' })).not.toBeInTheDocument()
  );
  fireEvent.click(screen.getByRole('button', { name: '公開許可を撤回' }));
  const revocation = await screen.findByRole('dialog', { name: '公開許可を撤回' });
  expect(within(revocation).getByLabelText('撤回を受けた日時（この端末の時刻）')).toBeVisible();
  expect(within(revocation).getByLabelText('判断・変更の理由')).toBeVisible();
});
test('訂正は本文を明示して新規受付し、公開許可を引き継がない説明を確認する', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.write.mockResolvedValue({
    review: { ...row, id: '11', supersedes_id: '10', body: '訂正本文' },
    operation: {
      id: '21',
      type: 'CORRECTION_RECEIVED',
      review_id: '11',
      committed_version: 0,
      replayed: false,
    },
  });
  render(<ReviewsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  fireEvent.click(await screen.findByRole('button', { name: '訂正再受付' }));
  const editor = await screen.findByRole('dialog', { name: '訂正再受付' });
  expect(within(editor).getByLabelText('口コミ本文')).toHaveValue('保護された本文');
  expect(within(editor).getByText(/公開許可は引き継ぎません/)).toBeVisible();
  fireEvent.change(within(editor).getByLabelText('口コミ本文'), { target: { value: '訂正本文' } });
  fireEvent.change(within(editor).getByLabelText('判断・変更の理由'), {
    target: { value: '誤記修正' },
  });
  fireEvent.click(within(editor).getByRole('button', { name: '確認へ' }));
  const confirmation = await screen.findByRole('alertdialog');
  expect(confirmation).toHaveTextContent('旧記録を取り下げ');
  fireEvent.click(within(confirmation).getByRole('button', { name: '確定する' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'CORRECT',
    id: '10',
    input: { version: 0, reason: '誤記修正', body: '訂正本文', origin_order_id: null },
  });
  expect(await screen.findByText('訂正本文', { selector: 'dd' })).toBeVisible();
});
test('表示名検索は適用した条件だけを送信し、取得失敗を空表示と区別する', async () => {
  render(<ReviewsPage />);
  await screen.findByRole('button', { name: '内容・履歴' });
  fireEvent.change(screen.getByLabelText('表示名で検索'), { target: { value: 'A%_名' } });
  expect(api.list).toHaveBeenCalledTimes(1);
  api.list.mockRejectedValueOnce(new Error('network'));
  fireEvent.click(screen.getByRole('button', { name: '検索' }));
  const failure = await screen.findByRole('alert');
  expect(failure).toHaveTextContent('口コミ一覧を取得できませんでした');
  expect(api.list).toHaveBeenLastCalledWith(0, expect.objectContaining({ q: 'A%_名' }));
  fireEvent.change(screen.getByLabelText('表示名で検索'), { target: { value: '未適用' } });
  api.list.mockResolvedValueOnce({ rows: [], page: 0, pageCount: 0, total: 0 });
  fireEvent.click(within(failure).getByRole('button'));
  await screen.findByText('口コミはありません');
  expect(api.list).toHaveBeenLastCalledWith(0, expect.objectContaining({ q: 'A%_名' }));
});
test('店舗変更後の旧一覧・書込み応答を表示しない', async () => {
  let resolveList!: (value: {
    rows: Review[];
    page: number;
    pageCount: number;
    total: number;
  }) => void;
  api.list
    .mockReturnValueOnce(
      new Promise(resolve => {
        resolveList = resolve;
      })
    )
    .mockResolvedValueOnce({ rows: [], page: 0, pageCount: 0, total: 0 });
  const view = render(<ReviewsPage />);
  mockStoreId = '2';
  view.rerender(<ReviewsPage />);
  await screen.findByText('口コミはありません');
  await act(async () => resolveList({ rows: [row], page: 0, pageCount: 1, total: 1 }));
  expect(screen.queryByRole('button', { name: '内容・履歴' })).not.toBeInTheDocument();
});
test('404の詳細は操作を表示せず一覧に戻れる', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE', 'REVIEW_MODERATE');
  api.get.mockRejectedValueOnce({ response: { status: 404 } });
  render(<ReviewsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.click(await within(dialog).findByRole('button', { name: '一覧へ戻る' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(api.write).not.toHaveBeenCalled();
});
async function approvalEditor() {
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  fireEvent.click(await screen.findByRole('button', { name: '内部承認' }));
  const editor = await screen.findByRole('dialog', { name: '内部承認' });
  fireEvent.change(within(editor).getByLabelText('判断・変更の理由'), {
    target: { value: '確認済み' },
  });
  fireEvent.click(within(editor).getByRole('button', { name: '確認へ' }));
  const confirm = await screen.findByRole('alertdialog');
  await act(async () => {
    fireEvent.click(within(confirm).getByRole('button', { name: '確定する' }));
  });
}
test('409は再送せず最新状態を確認して操作を選び直す', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write.mockRejectedValueOnce({ response: { status: 409 } });
  render(<ReviewsPage />);
  await approvalEditor();
  await screen.findByText(
    '口コミが更新されています。最新状態を確認してから、改めて操作を選んでください。'
  );
  await waitFor(() =>
    expect(screen.queryByRole('dialog', { name: '内部承認' })).not.toBeInTheDocument()
  );
  expect(screen.queryByRole('button', { name: '内部承認' })).not.toBeInTheDocument();
  expect(api.write).toHaveBeenCalledTimes(1);
  api.get.mockResolvedValue({ ...row, status: 'WITHDRAWN', version: 1 });
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認して戻る' }));
  await screen.findByText('取り下げ済み', { selector: '[data-slot="badge"]' });
  expect(api.write).toHaveBeenCalledTimes(1);
});
test('権限拒否後は旧画面の操作を除去し、不明要求のキーは保持する', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write
    .mockRejectedValueOnce(new Error('network'))
    .mockRejectedValueOnce({ response: { status: 403 } })
    .mockResolvedValueOnce({
      review: { ...row, status: 'APPROVED', version: 1 },
      operation: {
        id: '20',
        type: 'APPROVED',
        review_id: row.id,
        committed_version: 1,
        replayed: true,
      },
    });
  render(<ReviewsPage />);
  await approvalEditor();
  const editor = await screen.findByRole('dialog', { name: '内部承認' });
  await within(editor).findByText(/結果が確認できません/);
  fireEvent.keyDown(editor, { key: 'Escape', code: 'Escape' });
  fireEvent.keyDown(await screen.findByRole('dialog', { name: '口コミの内容・履歴' }), {
    key: 'Escape',
    code: 'Escape',
  });
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await screen.findByText(/現在の操作権限がありません/);
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(3));
  expect(api.write.mock.calls[2][0]).toEqual(api.write.mock.calls[0][0]);
});
test.each([401, 403])('詳細の認証・権限エラー %s を通信失敗と区別する', async status => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.get.mockRejectedValueOnce({ response: { status } });
  render(<ReviewsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '内容・履歴' }));
  const dialog = await screen.findByRole('dialog');
  const alert = await within(dialog).findByRole('alert');
  expect(alert).toHaveTextContent(
    status === 401 ? 'ログイン状態を確認してください' : '現在の閲覧権限がありません'
  );
  expect(within(dialog).queryByRole('button', { name: '訂正再受付' })).not.toBeInTheDocument();
});
test('結果不明後の409は確定した競合として解除し最新の版で改めて判断する', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write
    .mockRejectedValueOnce(new Error('network'))
    .mockRejectedValueOnce({ response: { status: 409 } });
  render(<ReviewsPage />);
  await approvalEditor();
  const editor = await screen.findByRole('dialog', { name: '内部承認' });
  await within(editor).findByText(/結果が確認できません/);
  fireEvent.keyDown(editor, { key: 'Escape', code: 'Escape' });
  await waitFor(() =>
    expect(screen.queryByRole('dialog', { name: '内部承認' })).not.toBeInTheDocument()
  );
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await screen.findByText(
    '口コミが更新されています。最新状態を確認してから、改めて操作を選んでください。'
  );
  api.get.mockResolvedValue({ ...row, version: 1 });
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認して戻る' }));
  const approve = await screen.findByRole('button', { name: '内部承認' });
  expect(approve).toBeEnabled();
  expect(screen.queryByRole('button', { name: '同じ要求で結果を確認' })).not.toBeInTheDocument();
  fireEvent.click(approve);
  const nextEditor = await screen.findByRole('dialog', { name: '内部承認' });
  fireEvent.change(within(nextEditor).getByLabelText('判断・変更の理由'), {
    target: { value: '更新後を確認' },
  });
  api.write.mockResolvedValue({
    review: { ...row, status: 'APPROVED', version: 2 },
    operation: {
      id: '22',
      type: 'APPROVED',
      review_id: row.id,
      committed_version: 2,
      replayed: false,
    },
  });
  fireEvent.click(within(nextEditor).getByRole('button', { name: '確認へ' }));
  const confirm = await screen.findByRole('alertdialog');
  fireEvent.click(within(confirm).getByRole('button', { name: '確定する' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(3));
  expect(api.write.mock.calls[2][0].input.dedupe_key).not.toBe(
    api.write.mock.calls[0][0].input.dedupe_key
  );
  expect(api.write.mock.calls[2][0]).toMatchObject({ input: { version: 1 } });
});

test('旧店舗で送信した応答が遅れて届いても新店舗に内容と完了通知を表示しない', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  let resolveWrite!: (value: Awaited<ReturnType<typeof reviewApi.write>>) => void;
  api.write.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        resolveWrite = resolve;
      })
  );
  const view = render(<ReviewsPage />);
  await approvalEditor();
  expect(api.write).toHaveBeenCalledTimes(1);
  api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
  mockStoreId = '2';
  view.rerender(<ReviewsPage />);
  await screen.findByText('口コミはありません');
  const listCalls = api.list.mock.calls.length;
  await act(async () =>
    resolveWrite({
      review: { ...row, status: 'APPROVED', version: 1 },
      operation: {
        id: '20',
        type: 'APPROVED',
        review_id: row.id,
        committed_version: 1,
        replayed: false,
      },
    })
  );
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(screen.queryByText('保護された本文')).not.toBeInTheDocument();
  expect(api.list).toHaveBeenCalledTimes(listCalls);
  expect(jest.mocked(notify.success)).not.toHaveBeenCalled();
});

test('操作404の詳細を閉じた後、別の有効な口コミを表示できる', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write.mockRejectedValueOnce({ response: { status: 404 } });
  const other = { ...row, id: '11', body: '別の口コミ本文' };
  api.list.mockResolvedValue({ rows: [row, other], page: 0, pageCount: 1, total: 2 });
  render(<ReviewsPage />);
  fireEvent.click((await screen.findAllByRole('button', { name: '内容・履歴' }))[0]);
  fireEvent.click(await screen.findByRole('button', { name: '内部承認' }));
  const editor = await screen.findByRole('dialog', { name: '内部承認' });
  fireEvent.change(within(editor).getByLabelText('判断・変更の理由'), {
    target: { value: '確認済み' },
  });
  fireEvent.click(within(editor).getByRole('button', { name: '確認へ' }));
  fireEvent.click(
    within(await screen.findByRole('alertdialog')).getByRole('button', { name: '確定する' })
  );
  await screen.findByText('口コミが見つかりません');
  fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' });
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  api.get.mockResolvedValue(other);
  fireEvent.click(screen.getAllByRole('button', { name: '内容・履歴' })[1]);
  expect(await screen.findByText('別の口コミ本文')).toBeVisible();
  expect(await screen.findByRole('button', { name: '内部承認' })).toBeEnabled();
});
