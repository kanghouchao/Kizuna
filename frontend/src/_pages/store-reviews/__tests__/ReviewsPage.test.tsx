import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ReviewsPage } from '../ui/ReviewsPage';
import { ReviewCreatePage } from '../ui/ReviewCreatePage';
import { reviewApi, Review } from '@/entities/content/review';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
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
  useParams: () => ({}),
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
  setCurrentStore('1');
  permission('REVIEW_VIEW');
  api.list.mockResolvedValue({ rows: [row], page: 0, pageCount: 1, total: 1 });
});
test('閲覧権限がなければ取得せず、閲覧のみは詳細へのリンクだけを出す', async () => {
  permission('REVIEW_MANAGE');
  const view = render(<ReviewsPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('閲覧権限がありません');
  expect(api.list).not.toHaveBeenCalled();
  permission('REVIEW_VIEW');
  view.rerender(<ReviewsPage />);
  expect(await screen.findByRole('link', { name: '内容・履歴' })).toHaveAttribute(
    'href',
    '/store/reviews/10'
  );
  expect(screen.queryByRole('link', { name: '口コミを受付' })).not.toBeInTheDocument();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});
test('管理権限があれば受付画面へのリンクを出す', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  render(<ReviewsPage />);
  expect(await screen.findByRole('link', { name: '口コミを受付' })).toHaveAttribute(
    'href',
    '/store/reviews/create'
  );
});
test('受付画面で結果不明になった操作は一覧でも案内し、再確認の結果の詳細へ移る', async () => {
  permission('REVIEW_VIEW', 'REVIEW_MANAGE');
  api.write.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
    review: { ...row, id: '12' },
    operation: {
      id: '20',
      type: 'RECEIVED',
      review_id: '12',
      committed_version: 0,
      replayed: true,
    },
  });
  const intake = render(<ReviewCreatePage />);
  fireEvent.change(screen.getByLabelText('口コミ本文'), { target: { value: '本文' } });
  fireEvent.change(screen.getByLabelText('受け取った日時（この端末の時刻）'), {
    target: { value: '2026-01-01T09:00' },
  });
  fireEvent.click(screen.getByRole('button', { name: '受付を記録' }));
  await screen.findByText(/結果が確認できません/);
  intake.unmount();
  render(<ReviewsPage />);
  const notice = await screen.findByRole('alert');
  expect(notice).toHaveTextContent('結果が確認できません');
  fireEvent.click(within(notice).getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/reviews/12'));
  expect(api.write).toHaveBeenCalledTimes(2);
  expect(api.write.mock.calls[1][1]).toBe(api.write.mock.calls[0][1]);
});
test('表示名検索は適用した条件だけを送信し、取得失敗を空表示と区別する', async () => {
  render(<ReviewsPage />);
  await screen.findByRole('link', { name: '内容・履歴' });
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
test('店舗変更後の旧一覧を表示しない', async () => {
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
  expect(screen.queryByRole('link', { name: '内容・履歴' })).not.toBeInTheDocument();
});
