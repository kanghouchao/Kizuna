import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { AdminMemberSummary, adminMemberApi } from '@/entities/member';
import { AdminMembersPage } from '../ui/AdminMembersPage';

jest.mock('@/entities/member', () => ({
  ...jest.requireActual('@/entities/member'),
  adminMemberApi: { list: jest.fn() },
}));

const api = jest.mocked(adminMemberApi);

const member = (override: Partial<AdminMemberSummary>): AdminMemberSummary => ({
  id: 1,
  member_code: '123456789012',
  display_name: '会員 花子',
  email: 'hanako@example.com',
  rank: 'SILVER',
  registered_at: '2026-10-01T10:00:00+09:00',
  ...override,
});

function pageOf(rows: AdminMemberSummary[]) {
  return { rows, page: 0, pageCount: 1, total: rows.length };
}

beforeEach(() => {
  jest.clearAllMocks();
  api.list.mockResolvedValue(pageOf([member({})]));
});

it('既定は有効な会員だけを検索語なしで取得し、コードは詳細へのリンクになる', async () => {
  render(<AdminMembersPage />);
  expect(await screen.findByText('会員 花子')).toBeInTheDocument();

  expect(api.list).toHaveBeenCalledWith({ page: 0, size: 20, status: 'ACTIVE', search: undefined });
  expect(screen.getByRole('link', { name: '123456789012' })).toHaveAttribute(
    'href',
    '/admin/members/1'
  );
  const table = within(screen.getByRole('table'));
  expect(table.getByText('シルバー')).toBeInTheDocument();
  expect(table.getByText('有効')).toBeInTheDocument();
});

it('状態を「退会済み」に切り替えると検索ボタンを待たずに取り直し、退会済みの行がそう名乗る', async () => {
  render(<AdminMembersPage />);
  await screen.findByText('会員 花子');
  api.list.mockResolvedValue(
    pageOf([
      member({ id: 2, display_name: '退会 太郎', withdrawn_at: '2026-10-20T10:00:00+09:00' }),
    ])
  );

  fireEvent.click(screen.getByRole('combobox', { name: '状態' }));
  const option = screen.getByRole('option', { name: '退会済み' });
  fireEvent.pointerDown(option);
  fireEvent.click(option);

  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'WITHDRAWN' }))
  );
  expect(await screen.findByText('退会 太郎')).toBeInTheDocument();
  expect(within(screen.getByRole('table')).getByText('退会済み')).toBeInTheDocument();
});

it('検索語は submit で適用され、適用済みの状態と一緒に送る', async () => {
  render(<AdminMembersPage />);
  await screen.findByText('会員 花子');

  fireEvent.change(screen.getByLabelText('検索語'), { target: { value: 'hanako@' } });
  expect(api.list).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: '検索' }));

  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      status: 'ACTIVE',
      search: 'hanako@',
    })
  );
});

it('取得に失敗したら一覧の領域で名乗り、再試行できる', async () => {
  api.list.mockRejectedValueOnce(new Error('boom'));
  render(<AdminMembersPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('会員一覧の取得に失敗しました');

  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('会員 花子')).toBeInTheDocument();
});
