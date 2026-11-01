import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AdminMemberDetail, adminMemberApi } from '@/entities/member';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { notify } from '@/shared/notify';
import { AdminMemberDetailPage } from '../ui/AdminMemberDetailPage';

jest.mock('next/navigation', () => ({ useParams: () => ({ id: '7' }) }));
jest.mock('@/entities/member', () => ({
  ...jest.requireActual('@/entities/member'),
  adminMemberApi: { get: jest.fn(), pointEntries: jest.fn(), withdraw: jest.fn() },
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const api = jest.mocked(adminMemberApi);
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const meWith = (permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: { email: 'staff@example.com', user_type: 'STAFF', permissions, line_linked: false },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });

const detail = (override: Partial<AdminMemberDetail>): AdminMemberDetail => ({
  id: 7,
  member_code: '123456789012',
  display_name: '会員 花子',
  email: 'hanako@example.com',
  line_linked: false,
  rank: 'SILVER',
  registered_at: '2026-10-01T10:00:00+09:00',
  balance: 300,
  balances: [{ expires_on: '2026-12-31', points: 100 }, { points: 200 }],
  rank_history: [
    { previous_rank: 'BRONZE', new_rank: 'SILVER', promoted_at: '2026-10-10T10:00:00+09:00' },
  ],
  links: [
    {
      id: 'L1',
      store_id: 1,
      store_name: '本店',
      customer_id: 'C1',
      customer_name: '山田',
      status: 'ACTIVE',
      reason: 'MEMBER_CODE',
      linked_at: '2026-10-05T10:00:00+09:00',
    },
  ],
  other_store_link_count: 2,
  ...override,
});

beforeEach(() => {
  jest.clearAllMocks();
  meWith([]);
  api.get.mockResolvedValue(detail({}));
  api.pointEntries.mockResolvedValue({
    rows: [
      {
        occurred_at: '2026-10-20T10:00:00+09:00',
        entry_type: 'WITHDRAWAL_CLEAR',
        amount: -300,
        reason: '会員退会',
        actor_name: '職員 太郎',
      },
      {
        occurred_at: '2026-10-11T10:00:00+09:00',
        entry_type: 'ORDER_GRANT',
        amount: 300,
        store_name: '本店',
      },
    ],
    page: 0,
    pageCount: 1,
    total: 2,
  });
});

it('身分・期限別残高・ランク履歴・店舗集合内の関連と集合外の件数・明細を表示する', async () => {
  render(<AdminMemberDetailPage />);
  expect(await screen.findByText('会員 花子')).toBeInTheDocument();

  expect(screen.getByText('300 pt')).toBeInTheDocument();
  expect(screen.getByText('2026-12-31')).toBeInTheDocument();
  expect(screen.getByText('期限なし')).toBeInTheDocument();
  expect(screen.getByText('ブロンズ → シルバー')).toBeInTheDocument();
  expect(screen.getByText('本店')).toBeInTheDocument();
  expect(screen.getByText('山田')).toBeInTheDocument();
  expect(screen.getByText('担当外の 2 店舗に関連があります。')).toBeInTheDocument();
  expect(await screen.findByText('退会消去')).toBeInTheDocument();
  expect(screen.getByText('-300')).toBeInTheDocument();
  expect(screen.getByText('職員 太郎')).toBeInTheDocument();
  expect(screen.getByText('会員退会')).toBeInTheDocument();
  expect(screen.getByText('獲得')).toBeInTheDocument();
});

it('MEMBER_WITHDRAW が無ければ退会操作を出さない', async () => {
  render(<AdminMemberDetailPage />);
  await screen.findByText('会員 花子');

  expect(screen.queryByRole('button', { name: '退会させる' })).not.toBeInTheDocument();
});

it('退会済みは操作の代わりに退会情報（日時・理由・操作者）を読み取り専用で出す', async () => {
  meWith(['MEMBER_WITHDRAW']);
  api.get.mockResolvedValue(
    detail({
      withdrawal: {
        withdrawn_at: '2026-10-20T10:00:00+09:00',
        reason: '本人からの電話依頼',
        by_self: false,
        withdrawn_by_name: '職員 太郎',
      },
      links: [],
      other_store_link_count: 0,
    })
  );
  render(<AdminMemberDetailPage />);
  await screen.findByText('会員 花子');

  expect(screen.getByText('退会情報')).toBeInTheDocument();
  expect(screen.getByText('本人からの電話依頼')).toBeInTheDocument();
  expect(screen.getAllByText('職員 太郎').length).toBeGreaterThan(0);
  expect(screen.queryByRole('button', { name: '退会させる' })).not.toBeInTheDocument();
});

it('MEMBER_WITHDRAW を持つ職員は理由を入力し「退会」と打って初めて実行でき、成功で取り直す', async () => {
  meWith(['MEMBER_WITHDRAW']);
  api.withdraw.mockResolvedValue(undefined);
  render(<AdminMemberDetailPage />);
  await screen.findByText('会員 花子');

  fireEvent.click(screen.getByRole('button', { name: '退会させる' }));
  const execute = await screen.findByRole('button', { name: '退会させる' });
  expect(execute).toBeDisabled();

  fireEvent.change(screen.getByLabelText('退会理由'), { target: { value: '本人からの電話依頼' } });
  fireEvent.change(screen.getByLabelText('確認のため「退会」と入力'), {
    target: { value: '退会' },
  });
  await waitFor(() => expect(screen.getByRole('button', { name: '退会させる' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '退会させる' }));

  await waitFor(() =>
    expect(api.withdraw).toHaveBeenCalledWith(7, { reason: '本人からの電話依頼' })
  );
  await waitFor(() => expect(api.get).toHaveBeenCalledTimes(2));
  expect(notify.success).toHaveBeenCalledWith('会員を退会させました');
});

it('理由が空のまま「退会」と打って押すと入力横に不備を出し、送らない', async () => {
  meWith(['MEMBER_WITHDRAW']);
  render(<AdminMemberDetailPage />);
  await screen.findByText('会員 花子');

  fireEvent.click(screen.getByRole('button', { name: '退会させる' }));
  await screen.findByLabelText('退会理由');
  fireEvent.change(screen.getByLabelText('確認のため「退会」と入力'), {
    target: { value: '退会' },
  });
  await waitFor(() => expect(screen.getByRole('button', { name: '退会させる' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '退会させる' }));

  expect(await screen.findByText('退会理由を入力してください')).toBeInTheDocument();
  expect(api.withdraw).not.toHaveBeenCalled();
});

it('存在しない会員は一覧への導線だけを出す', async () => {
  api.get.mockRejectedValue({ response: { status: 404 } });
  render(<AdminMemberDetailPage />);

  expect(await screen.findByText('会員が見つかりません')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '一覧に戻る' })).toHaveAttribute(
    'href',
    '/admin/members'
  );
});

it('明細はページ番号で送り、次のページを取り直す', async () => {
  api.pointEntries
    .mockResolvedValueOnce({
      rows: [
        {
          occurred_at: '2026-10-20T10:00:00+09:00',
          entry_type: 'USE',
          amount: -10,
          reason: '一頁目',
        },
      ],
      page: 0,
      pageCount: 2,
      total: 21,
    })
    .mockResolvedValueOnce({
      rows: [
        {
          occurred_at: '2026-10-01T10:00:00+09:00',
          entry_type: 'USE',
          amount: -20,
          reason: '二頁目',
        },
      ],
      page: 1,
      pageCount: 2,
      total: 21,
    });
  render(<AdminMemberDetailPage />);
  expect(await screen.findByText('一頁目')).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  expect(await screen.findByText('二頁目')).toBeInTheDocument();
  expect(api.pointEntries).toHaveBeenLastCalledWith(7, { page: 1, size: 20 });
});
