import { fireEvent, render, screen } from '@testing-library/react';
import { RoleResponse, platformRoleApi } from '@/entities/user';
import RolesPage from '../ui/RolesPage';

jest.mock('@/entities/user', () => ({
  platformRoleApi: { list: jest.fn(), remove: jest.fn() },
}));

jest.mock('../ui/RoleFormModal', () => {
  const React = require('react');
  return {
    RoleFormModal: ({ open }: { open: boolean }) =>
      open ? React.createElement('div', null, 'ロールモーダル表示中') : null,
  };
});

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedRoleApi = platformRoleApi as jest.Mocked<typeof platformRoleApi>;

const role = (override: Partial<RoleResponse>): RoleResponse => ({
  id: 1,
  name: '店長',
  permissions: [],
  system: false,
  version: 0,
  ...override,
});

describe('ロール一覧ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.list.mockResolvedValue([
      role({ id: 1, name: '店長' }),
      role({ id: 2, name: '受付担当' }),
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
