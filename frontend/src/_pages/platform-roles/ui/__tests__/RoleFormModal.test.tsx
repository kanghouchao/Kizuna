import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import type { PermissionResponse, RoleResponse } from '@/entities/user';
import { platformRoleApi } from '@/entities/user';
import { RoleFormModal } from '../RoleFormModal';

jest.mock('@/entities/user', () => ({
  platformRoleApi: {
    list: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    permissions: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedRoleApi = platformRoleApi as jest.Mocked<typeof platformRoleApi>;
const mockedToast = toast as jest.Mocked<typeof toast>;

const role = (override: Partial<RoleResponse> = {}): RoleResponse => ({
  id: 5,
  name: '受付担当',
  system: false,
  permissions: ['ORDER_MANAGE'],
  version: 3,
  ...override,
});

const renderModal = (props: Partial<React.ComponentProps<typeof RoleFormModal>> = {}) => {
  const onClose = jest.fn();
  const onSaved = jest.fn();
  render(<RoleFormModal open editing={role()} onClose={onClose} onSaved={onSaved} {...props} />);
  return { onClose, onSaved };
};

describe('ロール編集モーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.permissions.mockResolvedValue([
      { code: 'ORDER_MANAGE', console: 'STORE' },
      { code: 'CUSTOMER_MANAGE', console: 'STORE' },
      { code: 'STAFF_MANAGE', console: 'PLATFORM' },
    ]);
    mockedRoleApi.update.mockResolvedValue({} as never);
    mockedRoleApi.create.mockResolvedValue({} as never);
  });

  it('権限目録を console ごとに見出し付きで並べる', async () => {
    renderModal();

    expect(await screen.findByText('プラットフォーム')).toBeInTheDocument();
    expect(screen.getByText('店舗')).toBeInTheDocument();
    // 権限ラベルはバックエンドのコードをそのまま出す（日本語名は持たない）
    expect(screen.getByLabelText('ORDER_MANAGE')).toBeChecked();
    expect(screen.getByLabelText('CUSTOMER_MANAGE')).not.toBeChecked();
  });

  it('未知の console の権限も落とさず、コードそのままの見出しで末尾に出す', async () => {
    // 組はこちらの表ではなく目録から作る。表で作ると、バックエンドが Console を
    // 増やした日にその権限が静かに画面から消える（付与できなくなる）。
    // 型に無い値を敢えて流す（この試験の主題が「フロントがまだ知らない値」そのもののため）
    mockedRoleApi.permissions.mockResolvedValue([
      { code: 'ORDER_MANAGE', console: 'STORE' },
      { code: 'FUTURE_MANAGE', console: 'BILLING' } as unknown as PermissionResponse,
    ]);
    renderModal();

    expect(await screen.findByLabelText('FUTURE_MANAGE')).toBeInTheDocument();
    expect(screen.getByText('BILLING')).toBeInTheDocument();
  });

  it('編集保存は楽観ロックの version を往復する', async () => {
    renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedRoleApi.update).toHaveBeenCalledTimes(1));
    expect(mockedRoleApi.update.mock.calls[0][0]).toBe(5);
    expect(mockedRoleApi.update.mock.calls[0][1]).toEqual({
      name: '受付担当',
      permissions: ['ORDER_MANAGE'],
      version: 3,
    });
  });

  it('新規作成は version を送らない', async () => {
    renderModal({ editing: null });
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.change(screen.getByLabelText('ロール名'), { target: { value: '新ロール' } });
    fireEvent.click(screen.getByLabelText('CUSTOMER_MANAGE'));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedRoleApi.create).toHaveBeenCalledTimes(1));
    expect(mockedRoleApi.create.mock.calls[0][0]).toEqual({
      name: '新ロール',
      permissions: ['CUSTOMER_MANAGE'],
    });
  });

  it('権限を全て外すと保存 API を呼ばず警告する', async () => {
    renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByLabelText('ORDER_MANAGE'));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith('権限を 1 つ以上選択してください')
    );
    expect(mockedRoleApi.update).not.toHaveBeenCalled();
  });

  it('409 は一覧を再取得してモーダルを開いたままにする（version 固着で詰まないこと）', async () => {
    // 再取得しないと editing が古い version を抱えたままになり、再試行も開き直しも同じ 409 を繰り返す。
    mockedRoleApi.update.mockRejectedValue({ response: { status: 409 } });
    const { onClose, onSaved } = renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith(
        '他の管理者が更新しました。最新の内容を確認してください'
      )
    );
    expect(onSaved).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('409 以外の失敗は再取得せずサーバ文言を出す', async () => {
    mockedRoleApi.update.mockRejectedValue({
      response: { status: 400, data: { error: 'このロール名は既に使われています' } },
    });
    const { onClose, onSaved } = renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith('このロール名は既に使われています')
    );
    expect(onSaved).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
});
