import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import type { PlatformStaffResponse } from '@/entities/user';
import { platformRoleApi, platformStaffApi } from '@/entities/user';
import { StaffEditModal } from '../StaffEditModal';

jest.mock('@/entities/user', () => ({
  platformRoleApi: { list: jest.fn() },
  platformStaffApi: { update: jest.fn() },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedStaffApi = platformStaffApi as jest.Mocked<typeof platformStaffApi>;
const mockedRoleApi = platformRoleApi as jest.Mocked<typeof platformRoleApi>;
const mockedToast = toast as jest.Mocked<typeof toast>;

const staff = (override: Partial<PlatformStaffResponse> = {}): PlatformStaffResponse => ({
  id: 42,
  email: 'staff@example.com',
  display_name: '山田太郎',
  enabled: true,
  roles: [{ id: 3, name: '店長' }],
  store_scope_type: 'ALL_STORES',
  store_ids: [],
  version: 7,
  ...override,
});

const renderModal = (props: Partial<React.ComponentProps<typeof StaffEditModal>> = {}) => {
  const onClose = jest.fn();
  const onUpdated = jest.fn();
  render(
    <StaffEditModal
      staff={staff()}
      stores={[]}
      storesLoading={false}
      onClose={onClose}
      onUpdated={onUpdated}
      {...props}
    />
  );
  return { onClose, onUpdated };
};

describe('スタッフ授権編集モーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.list.mockResolvedValue([
      { id: 3, name: '店長', system: true, permission_count: 0 },
      { id: 4, name: '経理', system: false, permission_count: 0 },
    ]);
    mockedStaffApi.update.mockResolvedValue({} as never);
  });

  it('開くと対象スタッフ名の見出しを表示する', async () => {
    renderModal();

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('山田太郎 の権限を編集')).toBeInTheDocument();
  });

  it('保存は楽観ロックの version を含む現在値をそのまま送信する', async () => {
    renderModal();
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedStaffApi.update).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.update.mock.calls[0][0]).toBe(42);
    expect(mockedStaffApi.update.mock.calls[0][1]).toEqual({
      role_ids: [3],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
      enabled: true,
      version: 7,
    });
  });

  it('状態を停止へ切り替えると enabled=false で送信する', async () => {
    renderModal();
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByLabelText('停止'));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedStaffApi.update).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.update.mock.calls[0][1]).toMatchObject({ enabled: false });
  });

  it('ロールの選択を全て外すと更新 API を呼ばず警告する', async () => {
    renderModal({ staff: staff({ roles: [] }) });
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith('ロールを 1 つ以上選択してください')
    );
    expect(mockedStaffApi.update).not.toHaveBeenCalled();
  });

  it('保存成功で完了トーストを出し onUpdated と onClose を呼ぶ', async () => {
    const { onClose, onUpdated } = renderModal();
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledTimes(1));
    expect(mockedToast.success).toHaveBeenCalledWith('権限を更新しました');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('409 は固定文言で警告し一覧を再取得したままモーダルを閉じない', async () => {
    mockedStaffApi.update.mockRejectedValue({ response: { status: 409 } });
    const { onClose, onUpdated } = renderModal();
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith(
        '他の管理者が更新しました。最新の内容を確認してください'
      )
    );
    expect(onUpdated).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('409 以外の失敗は onUpdated を呼ばない', async () => {
    mockedStaffApi.update.mockRejectedValue({ response: { status: 400 } });
    const { onClose, onUpdated } = renderModal();
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedToast.error).toHaveBeenCalled());
    expect(mockedToast.error).not.toHaveBeenCalledWith(
      '他の管理者が更新しました。最新の内容を確認してください'
    );
    expect(onUpdated).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('キャンセルは更新せず閉じる', async () => {
    const { onClose } = renderModal();
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockedStaffApi.update).not.toHaveBeenCalled();
  });

  it('Escape で閉じる', async () => {
    const { onClose } = renderModal();
    await screen.findByRole('dialog');

    fireEvent.keyDown(document, { key: 'Escape' });

    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });
});
