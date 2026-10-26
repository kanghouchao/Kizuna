import { fireEvent, render, screen } from '@testing-library/react';
import { RoleSummaryResponse, roleApi } from '@/entities/user';
import RolesPage from '../ui/RolesPage';

jest.mock('@/entities/user', () => ({
  roleApi: { list: jest.fn(), remove: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedRoleApi = roleApi as jest.Mocked<typeof roleApi>;

const role = (override: Partial<RoleSummaryResponse>): RoleSummaryResponse => ({
  id: 1,
  name: '店長',
  permission_count: 3,
  system: false,
  ...override,
});

describe('ロール一覧ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.list.mockResolvedValue([
      role({ id: 1, name: '店長' }),
      role({ id: 2, name: '受付担当', permission_count: 1 }),
    ]);
  });

  // 一覧は権限個数までの要約で描画する（権限コードの列挙は編集画面が個別取得する）
  it('権限数は一覧応答の permission_count をそのまま表示すること', async () => {
    render(<RolesPage />);
    await screen.findByText('店長');

    expect(screen.getByText('3 件')).toBeInTheDocument();
    expect(screen.getByText('1 件')).toBeInTheDocument();
  });

  it('追加と編集は独立した画面へのリンクであること', async () => {
    render(<RolesPage />);
    await screen.findByText('店長');

    expect(screen.getByRole('link', { name: 'ロールを追加' })).toHaveAttribute(
      'href',
      '/admin/roles/create'
    );
    const editLinks = screen.getAllByRole('link', { name: '編集' });
    expect(editLinks.map(link => link.getAttribute('href'))).toEqual([
      '/admin/roles/1/edit',
      '/admin/roles/2/edit',
    ]);
  });

  // ロールは全量取得のため絞り込みは取得済み配列に対して行う。再取得は走らない。
  it('検索は再取得せず取得済みの一覧をロール名で絞り込むこと', async () => {
    render(<RolesPage />);
    await screen.findByText('店長');

    fireEvent.change(screen.getByLabelText('ロールを検索'), { target: { value: '受付' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    expect(screen.getByText('受付担当')).toBeInTheDocument();
    expect(screen.queryByText('店長')).not.toBeInTheDocument();
    expect(mockedRoleApi.list).toHaveBeenCalledTimes(1);
  });

  it('該当なしのときは検索向けの空文言を出し、クリアで全件へ戻ること', async () => {
    render(<RolesPage />);
    await screen.findByText('店長');

    fireEvent.change(screen.getByLabelText('ロールを検索'), { target: { value: '存在しない' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));
    expect(screen.getByText('該当するロールが見つかりません')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'クリア' }));

    expect(screen.getByText('店長')).toBeInTheDocument();
    expect(screen.getByText('受付担当')).toBeInTheDocument();
  });
});
