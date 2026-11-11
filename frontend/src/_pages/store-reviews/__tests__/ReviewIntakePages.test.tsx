import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { ReviewCreatePage } from '../ui/ReviewCreatePage';
import { ReviewEditPage } from '../ui/ReviewEditPage';
import { reviewApi, Review } from '@/entities/content/review';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { storeSessionToken } from '@/shared/api';

jest.mock('@/widgets/admin-shell', () => ({
  DeniedPage: () => <p role="alert">権限の無い画面</p>,
}));
let mockStoreId = '1';
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => mockStoreId,
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: mockStoreId,
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
  }),
}));
jest.mock('@/entities/content/review', () => ({
  reviewApi: { list: jest.fn(), get: jest.fn(), history: jest.fn(), write: jest.fn() },
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
const mockPush = jest.fn();
const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace, back: jest.fn() }),
  useParams: () => ({ id: '10' }),
}));
const api = jest.mocked(reviewApi);
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
  publication: { published: false, publish_from: null, publish_until: null, display_order: null },
  displayed: false,
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
  storeSessionToken({ token: 't', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
  permission('REVIEW_VIEW');
  api.get.mockResolvedValue(row);
});

const written = (review: Review) => ({
  review,
  operation: {
    id: '21',
    type: 'CORRECTION_RECEIVED' as const,
    review_id: review.id,
    committed_version: review.version,
    replayed: false,
  },
});
async function fillReceipt(kind: 'CREATE' | 'CORRECT') {
  fireEvent.change(await screen.findByLabelText('口コミ本文'), {
    target: { value: '失ってはいけない入力本文' },
  });
  fireEvent.change(screen.getByLabelText('受け取った日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  fireEvent.change(screen.getByLabelText('関連受注ID（任意）'), {
    target: { value: '00000000-0000-7000-8000-000000000999' },
  });
  if (kind === 'CORRECT')
    fireEvent.change(screen.getByLabelText('判断・変更の理由'), {
      target: { value: '保持する訂正理由' },
    });
}
async function submitReceipt(kind: 'CREATE' | 'CORRECT') {
  if (kind === 'CREATE') {
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '受付を記録' }));
    });
    return;
  }
  fireEvent.click(screen.getByRole('button', { name: '確認へ' }));
  const confirmation = await screen.findByRole('alertdialog');
  await act(async () => {
    fireEvent.click(within(confirmation).getByRole('button', { name: '確定する' }));
  });
}
test.each([
  ['受付', ReviewCreatePage, 'REVIEW_VIEW'],
  ['訂正', ReviewEditPage, 'REVIEW_VIEW'],
  ['訂正', ReviewEditPage, 'REVIEW_MANAGE'],
] as const)('%s画面は閲覧と管理の両権限がなければ出さない', (_, Page, only) => {
  permission(only);
  render(<Page />);
  expect(screen.getByRole('alert')).toHaveTextContent('権限の無い画面');
  expect(screen.queryByLabelText('口コミ本文')).not.toBeInTheDocument();
  expect(api.get).not.toHaveBeenCalled();
});
test('受付は原文の空白を保って送り、記録した口コミの詳細へ履歴を置き換えて移る', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.write.mockResolvedValue(written({ ...row, id: '12' }));
  render(<ReviewCreatePage />);
  expect(screen.getByRole('heading', { level: 1, name: '口コミを受付' })).toBeVisible();
  expect(screen.queryByLabelText('関連受注ID（任意）')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('口コミ本文'), {
    target: { value: '  原文の空白を保持  ' },
  });
  fireEvent.change(screen.getByLabelText('受け取った日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  await submitReceipt('CREATE');
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'CREATE',
    input: { body: '  原文の空白を保持  ', origin_order_id: null },
  });
  expect(mockReplace).toHaveBeenCalledWith('/store/1/reviews/12');
  expect(notify.success).toHaveBeenCalledWith('口コミの操作を記録しました');
});
test('受付の結果が不明なら新しい送信を止め、同じ要求の再確認で詳細へ移る', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.write
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce(written({ ...row, id: '12' }));
  render(<ReviewCreatePage />);
  fireEvent.change(screen.getByLabelText('口コミ本文'), { target: { value: '本文' } });
  fireEvent.change(screen.getByLabelText('受け取った日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  await submitReceipt('CREATE');
  expect(await screen.findByText(/結果が確認できません/)).toBeVisible();
  expect(screen.getByRole('button', { name: '受付を記録' })).toBeDisabled();
  expect(mockReplace).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/1/reviews/12'));
  expect(api.write.mock.calls[1][1]).toBe(api.write.mock.calls[0][1]);
});
test('受付の404（関連受注の不在）は入力を保って直せる', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE', 'ORDER_MANAGE');
  api.write.mockRejectedValueOnce({ response: { status: 404 } });
  render(<ReviewCreatePage />);
  await fillReceipt('CREATE');
  await submitReceipt('CREATE');
  expect(screen.getByLabelText('口コミ本文')).toHaveValue('失ってはいけない入力本文');
  expect(screen.getByRole('button', { name: '受付を記録' })).toBeEnabled();
  expect(screen.queryByText('口コミが見つかりません')).not.toBeInTheDocument();
});
test('訂正は元の本文を初期値にし、公開許可を引き継がない説明を経て訂正先の詳細へ移る', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.write.mockResolvedValue(written({ ...row, id: '11', supersedes_id: '10', body: '訂正本文' }));
  render(<ReviewEditPage />);
  expect(await screen.findByLabelText('口コミ本文')).toHaveValue('保護された本文');
  expect(api.get).toHaveBeenCalledWith('10');
  expect(screen.getByText(/公開許可は引き継ぎません/)).toBeVisible();
  expect(screen.getByRole('link', { name: '詳細へ戻る' })).toHaveAttribute(
    'href',
    '/store/1/reviews/10'
  );
  fireEvent.change(screen.getByLabelText('口コミ本文'), { target: { value: '訂正本文' } });
  fireEvent.change(screen.getByLabelText('判断・変更の理由'), { target: { value: '誤記修正' } });
  fireEvent.click(screen.getByRole('button', { name: '確認へ' }));
  expect(await screen.findByRole('alertdialog')).toHaveTextContent('旧記録を取り下げ');
  const confirmation = screen.getByRole('alertdialog');
  await act(async () => {
    fireEvent.click(within(confirmation).getByRole('button', { name: '確定する' }));
  });
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'CORRECT',
    id: '10',
    input: { version: 0, reason: '誤記修正', body: '訂正本文', origin_order_id: null },
  });
  expect(mockReplace).toHaveBeenCalledWith('/store/1/reviews/11');
});
test('訂正の404で元口コミが残っていれば、関連受注の不在として入力を保つ', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE', 'ORDER_MANAGE');
  api.write.mockRejectedValueOnce({ response: { status: 404 } });
  render(<ReviewEditPage />);
  await fillReceipt('CORRECT');
  await submitReceipt('CORRECT');
  await waitFor(() => expect(api.get).toHaveBeenCalledTimes(2));
  expect(screen.getByLabelText('口コミ本文')).toHaveValue('失ってはいけない入力本文');
  expect(screen.getByLabelText('判断・変更の理由')).toHaveValue('保持する訂正理由');
  expect(screen.queryByText('口コミが見つかりません')).not.toBeInTheDocument();
});
test('訂正の404で元口コミの照会も404なら入力を閉じ、失効を表示する', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE', 'ORDER_MANAGE');
  api.get.mockResolvedValueOnce(row).mockRejectedValueOnce({ response: { status: 404 } });
  api.write.mockRejectedValueOnce({ response: { status: 404 } });
  render(<ReviewEditPage />);
  await fillReceipt('CORRECT');
  await submitReceipt('CORRECT');
  expect(await screen.findByText('口コミが見つかりません')).toBeVisible();
  expect(screen.queryByLabelText('口コミ本文')).not.toBeInTheDocument();
});
test('訂正済みの口コミは訂正画面で入力を出さない', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.get.mockResolvedValue({ ...row, superseded_by_id: '11' });
  render(<ReviewEditPage />);
  expect(await screen.findByText('この口コミは訂正済みです')).toBeVisible();
  expect(screen.queryByLabelText('口コミ本文')).not.toBeInTheDocument();
});
test('訂正の409は入力を閉じ、読み直した最新の版で入力し直す', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE', 'ORDER_MANAGE');
  api.write.mockRejectedValueOnce({ response: { status: 409 } });
  render(<ReviewEditPage />);
  await fillReceipt('CORRECT');
  await submitReceipt('CORRECT');
  expect(await screen.findByText(/口コミが更新されています/)).toBeVisible();
  expect(screen.queryByLabelText('口コミ本文')).not.toBeInTheDocument();
  api.get.mockResolvedValue({ ...row, version: 3, body: '他の担当者の訂正前本文' });
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認して戻る' }));
  expect(await screen.findByLabelText('口コミ本文')).toHaveValue('他の担当者の訂正前本文');
  expect(api.write).toHaveBeenCalledTimes(1);
});
