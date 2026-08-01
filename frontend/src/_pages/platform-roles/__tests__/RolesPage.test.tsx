import { fireEvent, render, screen } from '@testing-library/react';
import { RoleSummaryResponse, platformRoleApi } from '@/entities/user';
import RolesPage from '../ui/RolesPage';

jest.mock('@/entities/user', () => ({
  platformRoleApi: { list: jest.fn(), remove: jest.fn() },
}));

jest.mock('../ui/RoleFormModal', () => {
  const React = require('react');
  return {
    // 実体は開いたときだけ mount される。mock はマーカーだけ出す。
    RoleFormModal: () => React.createElement('div', null, 'ロールモーダル表示中'),
  };
});

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedRoleApi = platformRoleApi as jest.Mocked<typeof platformRoleApi>;

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

  // 一覧は権限個数までの要約で描画する（権限コードの列挙は編集モーダルが個別取得する）
  it('権限数は一覧応答の permission_count をそのまま表示すること', async () => {
    render(<RolesPage />);
    await screen.findByText('店長');

    expect(screen.getByText('3 件')).toBeInTheDocument();
    expect(screen.getByText('1 件')).toBeInTheDocument();
  });

  it('モーダルは開くまで mount しないこと（権限目録の先読みを防ぐ）', async () => {
    render(<RolesPage />);
    await screen.findByText('店長');

    expect(screen.queryByText('ロールモーダル表示中')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'ロールを追加' }));
    expect(screen.getByText('ロールモーダル表示中')).toBeInTheDocument();
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
