import { startTransition } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { roleApi } from '@/entities/user';
import RoleCreatePage from '../RoleCreatePage';

const mockPush = jest.fn();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('@/entities/user', () => ({
  roleApi: { create: jest.fn(), permissions: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedRoleApi = roleApi as jest.Mocked<typeof roleApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;

// router.push と同じく何も返さず、自分の transition の中で画面を進める。返り値で遷移を終わらせる
const holdNavigation = () => {
  let arrive: () => void = () => {};
  const navigating = new Promise<void>(resolve => (arrive = resolve));
  mockPush.mockImplementationOnce(() => {
    startTransition(async () => {
      await navigating;
    });
  });
  return () => arrive();
};

const fillAndSave = async () => {
  fireEvent.change(await screen.findByLabelText('ロール名'), { target: { value: '新ロール' } });
  fireEvent.click(await screen.findByRole('checkbox', { name: '顧客の管理' }));
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
};

describe('ロール追加ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.permissions.mockResolvedValue([
      {
        tree: 'STORE',
        label: '店舗',
        groups: [
          {
            group: 'CUSTOMER',
            label: '顧客',
            permissions: [{ code: 'CUSTOMER_MANAGE', label: '顧客の管理' }],
          },
        ],
      },
    ]);
    mockedRoleApi.create.mockResolvedValue({} as never);
  });

  it('version を送らずに作成し、成功したら一覧へ戻る', async () => {
    render(<RoleCreatePage />);
    expect(screen.getByRole('heading', { name: 'ロールを追加' })).toBeInTheDocument();

    await fillAndSave();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/roles'));
    expect(mockedRoleApi.create).toHaveBeenCalledWith({
      name: '新ロール',
      permissions: ['CUSTOMER_MANAGE'],
    });
    expect(mockedNotify.success).toHaveBeenCalledWith('ロールを追加しました');
  });

  it('応答が返ってから一覧への遷移が終わるまで、保存ボタンを押せないままにする', async () => {
    const arrive = holdNavigation();
    render(<RoleCreatePage />);

    await fillAndSave();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/roles'));
    const saving = screen.getByRole('button', { name: '保存中...' });
    expect(saving).toBeDisabled();
    fireEvent.click(saving);
    expect(mockedRoleApi.create).toHaveBeenCalledTimes(1);

    await act(async () => arrive());
    expect(await screen.findByRole('button', { name: '保存する' })).toBeEnabled();
  });

  it('失敗したらサーバ文言を出し、画面に留まる', async () => {
    mockedRoleApi.create.mockRejectedValue({
      response: { status: 400, data: { error: 'このロール名は既に使われています' } },
    });
    render(<RoleCreatePage />);

    await fillAndSave();

    await waitFor(() =>
      expect(mockedNotify.error).toHaveBeenCalledWith('このロール名は既に使われています')
    );
    expect(mockPush).not.toHaveBeenCalled();
  });
});
