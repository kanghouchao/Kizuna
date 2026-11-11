import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ReviewDetailPage } from '../ui/ReviewDetailPage';
import { ReviewCreatePage } from '../ui/ReviewCreatePage';
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
  api.history.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
});

const written = (review: Review) => ({
  review,
  operation: {
    id: '20',
    type: 'APPROVED' as const,
    review_id: review.id,
    committed_version: review.version,
    replayed: false,
  },
});
async function confirm() {
  const confirmation = await screen.findByRole('alertdialog');
  await act(async () => {
    fireEvent.click(within(confirmation).getByRole('button', { name: '確定する' }));
  });
}
async function approve() {
  fireEvent.click(await screen.findByRole('button', { name: '内部承認' }));
  const section = screen.getByRole('region', { name: '内部承認' });
  fireEvent.change(within(section).getByLabelText('判断・変更の理由'), {
    target: { value: '確認済み' },
  });
  fireEvent.click(within(section).getByRole('button', { name: '確認へ' }));
  await confirm();
}
test('閲覧のみは手動記録と未設定を確認でき、操作を出さない', async () => {
  render(<ReviewDetailPage />);
  expect(await screen.findByText('保護された本文')).toBeVisible();
  expect(api.get).toHaveBeenCalledWith('10');
  expect(screen.getByRole('heading', { level: 1, name: '口コミの内容・履歴' })).toBeVisible();
  expect(screen.getByText('スタッフによる記録')).toBeVisible();
  expect(screen.getByText('公開連携は未設定')).toBeVisible();
  expect(screen.getByRole('link', { name: '一覧へ戻る' })).toHaveAttribute(
    'href',
    '/store/1/reviews'
  );
  expect(screen.queryByRole('button', { name: '内部承認' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: '訂正再受付' })).not.toBeInTheDocument();
});
test('閲覧権限がなければ取得しない', () => {
  permission('REVIEW_MANAGE');
  render(<ReviewDetailPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('権限の無い画面');
  expect(api.get).not.toHaveBeenCalled();
});
test('内部承認は詳細画面の節で理由を入力し、ダイアログを重ねず確認だけを経て反映する', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write.mockResolvedValue(
    written({ ...row, status: 'APPROVED', version: 1, publication_blockers: ['NO_PERMISSION'] })
  );
  render(<ReviewDetailPage />);
  fireEvent.click(await screen.findByRole('button', { name: '内部承認' }));
  const section = screen.getByRole('region', { name: '内部承認' });
  expect(within(section).getByRole('heading', { level: 2, name: '内部承認' })).toHaveFocus();
  expect(within(section).getByText(/内部承認のみ/)).toBeVisible();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  fireEvent.change(within(section).getByLabelText('判断・変更の理由'), {
    target: { value: '内容確認済み' },
  });
  fireEvent.click(within(section).getByRole('button', { name: '確認へ' }));
  expect(await screen.findByRole('alertdialog')).toBeVisible();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  await confirm();
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'DECIDE',
    id: '10',
    input: { version: 0, decision: 'APPROVE', reason: '内容確認済み' },
  });
  expect(api.write.mock.calls[0][2]).toEqual({ headers: { 'X-Store-ID': '1' } });
  expect(
    await screen.findByText('内部承認済み', { selector: '[data-slot="badge"]' })
  ).toBeVisible();
  expect(screen.queryByRole('region', { name: '内部承認' })).not.toBeInTheDocument();
  expect(api.get).toHaveBeenCalledTimes(1);
});
test('入力の節は取り消せ、送信しない', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  render(<ReviewDetailPage />);
  fireEvent.click(await screen.findByRole('button', { name: '却下' }));
  const section = screen.getByRole('region', { name: '却下' });
  fireEvent.click(within(section).getByRole('button', { name: 'キャンセル' }));
  expect(screen.queryByRole('region', { name: '却下' })).not.toBeInTheDocument();
  expect(api.write).not.toHaveBeenCalled();
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
  api.write.mockResolvedValueOnce(written(granted));
  render(<ReviewDetailPage />);
  fireEvent.click(await screen.findByRole('button', { name: '公開許可を記録' }));
  const section = screen.getByRole('region', { name: '公開許可を記録' });
  expect(within(section).getByText(/自店舗ウェブサイト/)).toBeVisible();
  fireEvent.change(within(section).getByLabelText('許可を受けた日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  fireEvent.change(within(section).getByLabelText('許可を確認した根拠'), {
    target: { value: '書面の記録' },
  });
  fireEvent.click(within(section).getByRole('button', { name: '確認へ' }));
  await confirm();
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
  const revocation = screen.getByRole('region', { name: '公開許可を撤回' });
  expect(within(revocation).getByLabelText('撤回を受けた日時（この端末の時刻）')).toBeVisible();
  expect(within(revocation).getByLabelText('判断・変更の理由')).toBeVisible();
});
test('訂正再受付は訂正画面へのリンクにする', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  render(<ReviewDetailPage />);
  expect(await screen.findByRole('link', { name: '訂正再受付' })).toHaveAttribute(
    'href',
    '/store/1/reviews/10/edit'
  );
});
test('409は再送せず最新状態を確認して操作を選び直す', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write.mockRejectedValueOnce({ response: { status: 409 } });
  render(<ReviewDetailPage />);
  await approve();
  await screen.findByText(
    '口コミが更新されています。最新状態を確認してから、改めて操作を選んでください。'
  );
  expect(screen.queryByRole('region', { name: '内部承認' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '内部承認' })).not.toBeInTheDocument();
  api.get.mockResolvedValue({ ...row, status: 'WITHDRAWN', version: 1 });
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認して戻る' }));
  await screen.findByText('取り下げ済み', { selector: '[data-slot="badge"]' });
  expect(api.write).toHaveBeenCalledTimes(1);
});
test('操作が404なら口コミの失効を表示し、一覧へのリンクだけを出す', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.write.mockRejectedValueOnce({ response: { status: 404 } });
  render(<ReviewDetailPage />);
  await approve();
  const missing = await screen.findByText('口コミが見つかりません');
  expect(within(missing.parentElement!).getByRole('link', { name: '一覧へ戻る' })).toHaveAttribute(
    'href',
    '/store/1/reviews'
  );
  expect(screen.queryByText('保護された本文')).not.toBeInTheDocument();
});
test('URL の口コミが無ければ操作を出さず一覧へのリンクを出す', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE', 'REVIEW_MODERATE');
  api.get.mockRejectedValueOnce({ response: { status: 404 } });
  render(<ReviewDetailPage />);
  expect(await screen.findByText('口コミが見つかりません')).toBeVisible();
  expect(screen.queryByRole('button', { name: '内部承認' })).not.toBeInTheDocument();
  expect(api.write).not.toHaveBeenCalled();
});
test.each([401, 403])('詳細の認証・権限エラー %s を通信失敗と区別する', async status => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.get.mockRejectedValueOnce({ response: { status } });
  render(<ReviewDetailPage />);
  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent(
    status === 401 ? 'ログイン状態を確認してください' : '現在の閲覧権限がありません'
  );
  expect(screen.queryByRole('link', { name: '訂正再受付' })).not.toBeInTheDocument();
});
test('公開設定は店舗の現地時刻で入出力し、空欄の表示順を null として版付きで送る', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MODERATE');
  api.get.mockResolvedValue({
    ...row,
    version: 4,
    publication: {
      published: false,
      publish_from: '2026-03-01T11:00:00Z',
      publish_until: null,
      display_order: 2,
    },
  });
  api.write.mockResolvedValue(written({ ...row, version: 5 }));
  render(<ReviewDetailPage />);
  fireEvent.click(await screen.findByRole('button', { name: '公開設定を変更' }));
  const section = screen.getByRole('region', { name: '公開設定を変更' });
  const from = within(section).getByLabelText(/公開開始/);
  expect(from).toHaveValue('2026-03-01T20:00');
  const until = within(section).getByLabelText(/公開終了/);
  fireEvent.change(until, { target: { value: '2026-03-01T20:00' } });
  fireEvent.click(within(section).getByRole('button', { name: '確認へ' }));
  expect(await within(section).findByText('公開終了は公開開始より後にしてください')).toBeVisible();
  fireEvent.change(until, { target: { value: '2026-03-02T09:30' } });
  fireEvent.change(within(section).getByLabelText(/表示順/), { target: { value: '' } });
  fireEvent.click(within(section).getByRole('switch', { name: '公式サイトで公開する' }));
  fireEvent.click(within(section).getByRole('button', { name: '確認へ' }));
  await confirm();
  expect(api.write.mock.calls[0][0]).toEqual({
    kind: 'PUBLISH',
    id: row.id,
    input: {
      version: 4,
      published: true,
      publish_from: '2026-03-01T11:00:00.000Z',
      publish_until: '2026-03-02T00:30:00.000Z',
      display_order: null,
    },
  });
});
test('結果不明の操作は詳細でも新しい操作を止め、再確認が別の口コミを返せばその詳細へ移る', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE', 'REVIEW_MODERATE');
  api.write
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce(written({ ...row, id: '12' }));
  const intake = render(<ReviewCreatePage />);
  fireEvent.change(screen.getByLabelText('口コミ本文'), { target: { value: '本文' } });
  fireEvent.change(screen.getByLabelText('受け取った日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  fireEvent.click(screen.getByRole('button', { name: '受付を記録' }));
  await screen.findByText(/結果が確認できません/);
  intake.unmount();
  render(<ReviewDetailPage />);
  expect(await screen.findByRole('button', { name: '内部承認' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/1/reviews/12'));
});
