import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { PermissionGroupResponse } from '@/entities/user';
import { roleApi } from '@/entities/user';
import { RoleForm } from '../RoleForm';

jest.mock('@/entities/user', () => ({
  roleApi: { permissions: jest.fn() },
}));

const mockedRoleApi = roleApi as jest.Mocked<typeof roleApi>;

const catalog: PermissionGroupResponse[] = [
  {
    group: 'BUSINESS',
    label: '業務',
    permissions: [
      { code: 'ORDER_MANAGE', label: '受注の管理' },
      { code: 'SERVICE_MANAGE', label: 'サービス設定の管理' },
    ],
  },
  {
    group: 'CUSTOMER',
    label: '顧客',
    permissions: [{ code: 'CUSTOMER_MANAGE', label: '顧客の管理' }],
  },
];

describe('ロールフォーム', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.permissions.mockResolvedValue(catalog);
  });

  it('権限を業務領域ごとの見出しの下に並べ、日本語名を項目名にする', async () => {
    render(<RoleForm onSubmit={jest.fn()} />);

    await screen.findByRole('checkbox', { name: '受注の管理' });
    const group = screen.getByRole('group', { name: '権限' });
    expect(
      within(group)
        .getAllByRole('heading')
        .map(h => h.textContent)
    ).toEqual(['業務', '顧客']);
    expect(within(group).getByRole('checkbox', { name: '受注の管理' })).not.toBeChecked();
    expect(within(group).queryByText('ORDER_MANAGE')).not.toBeInTheDocument();
  });

  it('現在値を初期値にし、保存では名称と権限を渡す', async () => {
    const onSubmit = jest.fn().mockResolvedValue(undefined);
    render(
      <RoleForm
        initial={{ name: '受付担当', permissions: ['ORDER_MANAGE'], system: false }}
        onSubmit={onSubmit}
      />
    );
    expect(await screen.findByRole('checkbox', { name: '受注の管理' })).toBeChecked();
    expect(screen.getByLabelText('ロール名')).toHaveValue('受付担当');

    fireEvent.click(screen.getByRole('checkbox', { name: '顧客の管理' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toEqual({
      name: '受付担当',
      permissions: ['ORDER_MANAGE', 'CUSTOMER_MANAGE'],
    });
  });

  it('既定ロールは名称を固定する', async () => {
    render(
      <RoleForm
        initial={{ name: '統括', permissions: ['ORDER_MANAGE'], system: true }}
        onSubmit={jest.fn()}
      />
    );
    await screen.findByRole('checkbox', { name: '受注の管理' });

    expect(screen.getByLabelText('ロール名')).toBeDisabled();
  });

  it('権限が 1 つも無ければ組の傍に文言を出し、先頭の権限へ焦点を移して送信しない', async () => {
    const onSubmit = jest.fn();
    render(<RoleForm onSubmit={onSubmit} />);
    await screen.findByRole('checkbox', { name: '受注の管理' });

    fireEvent.change(screen.getByLabelText('ロール名'), { target: { value: '新ロール' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    const message = await screen.findByText('権限を 1 つ以上選択してください');
    const group = screen.getByRole('group', { name: '権限' });
    expect(group).toHaveAttribute('aria-invalid', 'true');
    expect(group.getAttribute('aria-describedby')).toContain(message.id);
    expect(document.activeElement).toBe(screen.getByRole('checkbox', { name: '受注の管理' }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('ロール名が空なら欄の傍に文言を出して送信しない', async () => {
    const onSubmit = jest.fn();
    render(<RoleForm onSubmit={onSubmit} />);
    await screen.findByRole('checkbox', { name: '受注の管理' });

    fireEvent.click(screen.getByRole('checkbox', { name: '受注の管理' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('ロール名を入力してください')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('権限目録の取得に失敗したら再試行で回復できる', async () => {
    mockedRoleApi.permissions.mockRejectedValueOnce({ response: { status: 500 } });
    render(<RoleForm onSubmit={jest.fn()} />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('権限目録の取得に失敗しました')).toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByRole('checkbox', { name: '受注の管理' })).toBeInTheDocument();
  });

  it('キャンセルは一覧へのリンク', async () => {
    render(<RoleForm onSubmit={jest.fn()} />);
    await screen.findByRole('checkbox', { name: '受注の管理' });

    expect(screen.getByRole('link', { name: 'キャンセル' })).toHaveAttribute(
      'href',
      '/admin/roles'
    );
  });

  it('送信中は保存ボタンを無効にして二重送信を防ぐ', async () => {
    render(
      <RoleForm
        initial={{ name: '受付担当', permissions: ['ORDER_MANAGE'], system: false }}
        onSubmit={() => new Promise(() => {})}
      />
    );
    await screen.findByRole('checkbox', { name: '受注の管理' });

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByRole('button', { name: '保存中...' })).toBeDisabled();
  });
});
