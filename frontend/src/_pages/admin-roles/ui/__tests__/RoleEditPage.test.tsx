import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import type { RoleResponse } from '@/entities/user';
import { roleApi } from '@/entities/user';
import RoleEditPage from '../RoleEditPage';

const mockPush = jest.fn();
let mockRouteId = '5';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => ({ id: mockRouteId }),
}));

jest.mock('@/entities/user', () => ({
  roleApi: { get: jest.fn(), update: jest.fn(), permissions: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedRoleApi = roleApi as jest.Mocked<typeof roleApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;

const role = (override: Partial<RoleResponse> = {}): RoleResponse => ({
  id: 5,
  name: '受付担当',
  system: false,
  permissions: ['ORDER_MANAGE'],
  version: 3,
  ...override,
});

describe('ロール編集ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRouteId = '5';
    mockedRoleApi.permissions.mockResolvedValue([
      {
        group: 'BUSINESS',
        label: '業務',
        permissions: [{ code: 'ORDER_MANAGE', label: '受注の管理' }],
      },
    ]);
    mockedRoleApi.get.mockResolvedValue(role());
    mockedRoleApi.update.mockResolvedValue({} as never);
  });

  it('id でロールを取得して見出しと欄を初期化する', async () => {
    render(<RoleEditPage />);

    expect(await screen.findByRole('heading', { name: '受付担当 を編集' })).toBeInTheDocument();
    expect(mockedRoleApi.get).toHaveBeenCalledWith(5);
    expect(screen.getByLabelText('ロール名')).toHaveValue('受付担当');
    expect(await screen.findByRole('checkbox', { name: '受注の管理' })).toBeChecked();
  });

  it('保存は取得した version を往復し、成功したら一覧へ戻る', async () => {
    render(<RoleEditPage />);
    await screen.findByRole('checkbox', { name: '受注の管理' });

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/roles'));
    expect(mockedRoleApi.update).toHaveBeenCalledWith(5, {
      name: '受付担当',
      permissions: ['ORDER_MANAGE'],
      version: 3,
    });
  });

  it('見つからないロールは行き止まりを述べ、再試行を出さない', async () => {
    mockedRoleApi.get.mockRejectedValue({ response: { status: 404 } });
    render(<RoleEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('このロールは見つかりませんでした')).toBeInTheDocument();
    expect(within(region).getByRole('link', { name: 'ロール一覧へ' })).toHaveAttribute(
      'href',
      '/admin/roles'
    );
    expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
  });

  it('数字でない id は取りに行かず、見つからないとして一覧への導線だけを出す', async () => {
    mockRouteId = 'abc';
    render(<RoleEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('このロールは見つかりませんでした')).toBeInTheDocument();
    expect(mockedRoleApi.get).not.toHaveBeenCalled();
  });

  it('取得に失敗したら再試行で回復できる', async () => {
    mockedRoleApi.get.mockRejectedValueOnce({ response: { status: 500 } });
    render(<RoleEditPage />);

    const region = await screen.findByRole('alert');
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByLabelText('ロール名')).toHaveValue('受付担当');
  });

  it('409 は警告を出して取り直し、新しい version で保存し直せる', async () => {
    mockedRoleApi.update.mockRejectedValueOnce({ response: { status: 409 } });
    mockedRoleApi.get.mockResolvedValueOnce(role()).mockResolvedValueOnce(role({ version: 4 }));
    render(<RoleEditPage />);
    await screen.findByRole('checkbox', { name: '受注の管理' });

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedNotify.warning).toHaveBeenCalledWith(
        '他の管理者が更新しました。最新の内容を確認してください'
      )
    );
    await waitFor(() => expect(mockedRoleApi.get).toHaveBeenCalledTimes(2));
    expect(mockPush).not.toHaveBeenCalled();

    await waitFor(() => expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));
    await waitFor(() => expect(mockedRoleApi.update).toHaveBeenCalledTimes(2));
    expect(mockedRoleApi.update.mock.calls[1][1]).toEqual(expect.objectContaining({ version: 4 }));
  });

  it('409 以外の失敗は再取得せずサーバ文言を出す', async () => {
    mockedRoleApi.update.mockRejectedValue({
      response: { status: 400, data: { error: 'このロール名は既に使われています' } },
    });
    render(<RoleEditPage />);
    await screen.findByRole('checkbox', { name: '受注の管理' });

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedNotify.error).toHaveBeenCalledWith('このロール名は既に使われています')
    );
    expect(mockedRoleApi.get).toHaveBeenCalledTimes(1);
    expect(mockPush).not.toHaveBeenCalled();
  });
});
