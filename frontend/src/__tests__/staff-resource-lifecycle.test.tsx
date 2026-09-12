import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import StaffPage from '@/_pages/platform-staff/ui/StaffPage';
import StoreStaffPage from '@/_pages/store-staff/ui/StoreStaffPage';
import ServiceIdentitiesPage from '@/_pages/platform-service-identities/ui/ServiceIdentitiesPage';
import {
  platformStaffApi,
  storeStaffApi,
  serviceIdentityApi,
  platformRoleApi,
  platformAuthApi,
  useStoreContext,
} from '@/entities/user';
import { notify } from '@/shared/notify';

jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1' }) }));
jest.mock('@/entities/user', () => ({
  platformStaffApi: { list: jest.fn(), get: jest.fn(), update: jest.fn() },
  storeStaffApi: { list: jest.fn(), get: jest.fn(), update: jest.fn(), grantableRoles: jest.fn() },
  serviceIdentityApi: {
    list: jest.fn(),
    get: jest.fn(),
    update: jest.fn(),
    grantableRoles: jest.fn(),
  },
  platformRoleApi: { list: jest.fn() },
  platformAuthApi: { stores: jest.fn() },
  useStoreContext: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
const detail = {
  id: 1,
  display_name: '詳細の名前',
  email: 'staff@example.com',
  enabled: true,
  editable: true,
  roles: [{ id: 3, name: '経理' }],
  store_scope_type: 'SPECIFIC_STORES' as const,
  store_ids: [1],
  version: 2,
};
const cases = [
  { name: '管理者', Page: StaffPage, api: platformStaffApi },
  { name: '店舗スタッフ', Page: StoreStaffPage, api: storeStaffApi },
  { name: 'サービスID', Page: ServiceIdentitiesPage, api: serviceIdentityApi },
];

describe.each(cases)('$name の詳細ライフサイクル', ({ Page, api }) => {
  const mocked = api as jest.Mocked<typeof api>;
  beforeEach(() => {
    jest.resetAllMocks();
    (platformAuthApi.stores as jest.Mock).mockResolvedValue([{ id: 1, name: '本店' }]);
    (useStoreContext as jest.Mock).mockReturnValue({
      stores: [{ id: 1, name: '本店' }],
      currentStoreId: '1',
      loadFailed: false,
      reload: jest.fn(),
    });
    for (const fetcher of [
      platformRoleApi.list,
      storeStaffApi.grantableRoles,
      serviceIdentityApi.grantableRoles,
    ]) {
      (fetcher as jest.Mock).mockResolvedValue([
        { id: 3, name: '経理', system: false, permission_count: 1 },
      ]);
    }
    mocked.list.mockResolvedValue({
      rows: [{ ...detail, display_name: '一覧の古い名前', roles: [] }],
      page: 0,
      pageCount: 1,
      total: 1,
    });
    mocked.get.mockResolvedValue(detail);
    mocked.update.mockResolvedValue(detail);
  });

  test('クリック直後に開き、一覧行で初期化せず、詳細失敗は領域内で再試行する', async () => {
    const pending = deferred<typeof detail>();
    mocked.get.mockReturnValueOnce(pending.promise);
    render(<Page />);
    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('読み込み中...')).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();
    expect(within(dialog).queryByText(/一覧の古い名前/)).not.toBeInTheDocument();
    await act(async () => pending.reject(new Error('network')));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('詳細を取得できませんでした。');
    expect(notify.error).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: '再試行' }));
    expect(await within(dialog).findByText('詳細の名前 の権限を編集')).toBeInTheDocument();
    expect(await within(dialog).findByRole('button', { name: '保存する' })).toBeInTheDocument();
  });

  test('409 の再取得中は編集できず、成功後の全項目と version で再開する', async () => {
    const pending = deferred<typeof detail>();
    mocked.get.mockResolvedValueOnce(detail).mockReturnValueOnce(pending.promise);
    mocked.update.mockRejectedValueOnce({ response: { status: 409 } });
    render(<Page />);
    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));
    await waitFor(() => expect(mocked.get).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();
    expect(notify.warning).not.toHaveBeenCalled();
    await act(async () => pending.resolve({ ...detail, version: 3, display_name: '最新の名前' }));
    expect(await screen.findByText('最新の名前 の権限を編集')).toBeInTheDocument();
    expect(notify.warning).toHaveBeenCalledTimes(1);
    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));
    await waitFor(() => expect(mocked.update).toHaveBeenCalledTimes(2));
    expect(mocked.update.mock.calls[1][1]).toMatchObject({
      version: 3,
      role_ids: [3],
      store_ids: [1],
    });
  });

  test('409 後の取得失敗では置換を通知せず、404 は保存を外して閉じる際に一覧を更新する', async () => {
    mocked.get.mockResolvedValueOnce(detail).mockRejectedValueOnce({ response: { status: 404 } });
    mocked.update.mockRejectedValueOnce({ response: { status: 409 } });
    render(<Page />);
    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('この対象は見つかりませんでした。');
    expect(notify.warning).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
    await waitFor(() => expect(mocked.list).toHaveBeenCalledTimes(2));
  });

  test('未着の詳細を閉じ、同じ ID を開き直した後の古い失敗を無視する', async () => {
    const pending = deferred<typeof detail>();
    mocked.get.mockReturnValueOnce(pending.promise);
    render(<Page />);
    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    expect(await screen.findByRole('button', { name: '保存する' })).toBeInTheDocument();
    await act(async () => pending.reject({ response: { status: 404 } }));
    expect(screen.getByRole('button', { name: '保存する' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
