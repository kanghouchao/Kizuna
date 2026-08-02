import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { platformRoleApi, platformStaffApi } from '@/entities/user';
import { StaffCreateModal } from '../StaffCreateModal';

jest.mock('@/entities/user', () => ({
  platformRoleApi: { list: jest.fn() },
  platformStaffApi: { create: jest.fn() },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedStaffApi = platformStaffApi as jest.Mocked<typeof platformStaffApi>;
const mockedRoleApi = platformRoleApi as jest.Mocked<typeof platformRoleApi>;
const mockedToast = toast as jest.Mocked<typeof toast>;

const stores = [{ id: 9, name: '店舗A' }];

const renderModal = (props: Partial<React.ComponentProps<typeof StaffCreateModal>> = {}) => {
  const onClose = jest.fn();
  const onCreated = jest.fn();
  render(
    <StaffCreateModal
      stores={stores}
      storesLoading={false}
      onClose={onClose}
      onCreated={onCreated}
      {...props}
    />
  );
  return { onClose, onCreated };
};

const fillBasics = () => {
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'new@example.com' },
  });
  fireEvent.change(screen.getByLabelText('初期パスワード'), { target: { value: 'secret' } });
  fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '佐藤次郎' } });
};

describe('スタッフ新規作成モーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.list.mockResolvedValue([
      { id: 3, name: '店長', system: true, permission_count: 0 },
      { id: 4, name: '経理', system: false, permission_count: 0 },
    ]);
    mockedStaffApi.create.mockResolvedValue({} as never);
  });

  it('mount 時（= 開いた時点）にロール目録を取得する', async () => {
    renderModal();

    await screen.findByLabelText('店長');
    expect(mockedRoleApi.list).toHaveBeenCalledTimes(1);
  });

  it('入力値と選択状態を snake_case のまま作成 API へ送る', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.create).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.create.mock.calls[0][0]).toEqual({
      email: 'new@example.com',
      password: 'secret',
      display_name: '佐藤次郎',
      role_ids: [3],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
    });
  });

  it('個別店舗を選ぶと店舗集合が SPECIFIC_STORES と店舗 id で送られる', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByLabelText('個別店舗'));
    fireEvent.click(await screen.findByLabelText('店舗A'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.create).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.create.mock.calls[0][0]).toMatchObject({
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [9],
    });
  });

  it('ロールが未選択なら作成 API を呼ばず警告する', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith('ロールを 1 つ以上選択してください')
    );
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  it('必須項目が空なら作成 API を呼ばない', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedRoleApi.list).toHaveBeenCalled());
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  it('作成成功で onCreated と onClose を呼ぶ', async () => {
    const { onClose, onCreated } = renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
    expect(mockedToast.success).toHaveBeenCalledWith('スタッフを追加しました');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('キャンセルは作成せず閉じる', async () => {
    const { onClose } = renderModal();
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });
});
