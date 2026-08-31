import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
import {
  ServiceIdentityResponse,
  ServiceIdentitySummaryResponse,
  platformAuthApi,
  serviceIdentityApi,
} from '@/entities/user';
import { notify } from '@/shared/notify';
import ServiceIdentitiesPage from '../ui/ServiceIdentitiesPage';

jest.mock('@/entities/user', () => ({
  serviceIdentityApi: {
    list: jest.fn(),
    get: jest.fn(),
    suspend: jest.fn(),
    resume: jest.fn(),
  },
  platformAuthApi: {
    stores: jest.fn(),
  },
}));

jest.mock('@/features/staff-management', () => ({
  roleSetLabel: (roles: { name?: string }[] | undefined) =>
    roles && roles.length > 0 ? roles.map(role => role.name).join('・') : '未選択',
  storeSetLabel: (
    scopeType: string | undefined,
    storeIds: number[] | undefined,
    stores: { id?: number; name?: string }[]
  ) =>
    scopeType === 'ALL_STORES'
      ? '全店舗'
      : stores
          .filter(store => store.id !== undefined && (storeIds ?? []).includes(store.id))
          .map(store => store.name)
          .join('・') || '未選択',
  ServiceIdentityCreateModal: () => <div>作成モーダル</div>,
  ServiceIdentityEditModal: ({ identity }: { identity: ServiceIdentityResponse }) => (
    <div>編集モーダル: {identity.display_name}</div>
  ),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = serviceIdentityApi as jest.Mocked<typeof serviceIdentityApi>;
const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;

const identity = (
  override: Partial<ServiceIdentitySummaryResponse>
): ServiceIdentitySummaryResponse => ({
  id: 1,
  display_name: '夜間バッチ',
  enabled: true,
  roles: [{ id: 3, name: 'バッチ実行' }],
  store_scope_type: 'SPECIFIC_STORES',
  store_ids: [10],
  ...override,
});

const detail = (override: Partial<ServiceIdentityResponse>): ServiceIdentityResponse => ({
  ...identity({}),
  version: 0,
  ...override,
});

const paginated = (
  rows: ServiceIdentitySummaryResponse[]
): PageResult<ServiceIdentitySummaryResponse> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
});

describe('サービスID管理ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.suspend.mockResolvedValue(undefined);
    mockedApi.resume.mockResolvedValue(undefined);
    mockedAuthApi.stores.mockResolvedValue([{ id: 10, name: '本店' }]);
    mockedApi.list.mockResolvedValue(
      paginated([
        identity({ id: 1, display_name: '夜間バッチ', enabled: true }),
        identity({
          id: 2,
          display_name: '外部連携',
          enabled: false,
          roles: [{ id: 4, name: '連携用' }],
          store_scope_type: 'ALL_STORES',
          store_ids: [],
        }),
      ])
    );
  });

  it('用途名・ロール・対象店舗・状態を一覧表示すること', async () => {
    render(<ServiceIdentitiesPage />);

    expect(await screen.findByText('夜間バッチ')).toBeInTheDocument();
    expect(screen.getByText('外部連携')).toBeInTheDocument();
    expect(screen.getByText('バッチ実行')).toBeInTheDocument();
    expect(screen.getByText('連携用')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('本店')).toBeInTheDocument());
    expect(screen.getByText('全店舗')).toBeInTheDocument();
    expect(screen.getByText('有効')).toBeInTheDocument();
    expect(screen.getByText('停止中')).toBeInTheDocument();
  });

  it('有効な行には停止だけ、停止中の行には再開だけを置くこと', async () => {
    render(<ServiceIdentitiesPage />);
    await screen.findByText('夜間バッチ');

    expect(screen.getAllByRole('button', { name: '停止' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: '再開' })).toHaveLength(1);
  });

  // 停止は定期処理を止める操作のため、確認を経てからでないと実行しない
  it('停止は確認してから実行し、成功後に一覧を取り直すこと', async () => {
    render(<ServiceIdentitiesPage />);
    fireEvent.click(await screen.findByRole('button', { name: '停止' }));

    expect(await screen.findByText('サービスIDを停止しますか？')).toBeInTheDocument();
    expect(mockedApi.suspend).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '停止する' }));

    await waitFor(() => expect(mockedApi.suspend).toHaveBeenCalledWith(1));
    expect(notify.success).toHaveBeenCalledWith('サービスIDを停止しました');
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('再開は確認なしで実行し、成功後に一覧を取り直すこと', async () => {
    render(<ServiceIdentitiesPage />);
    fireEvent.click(await screen.findByRole('button', { name: '再開' }));

    await waitFor(() => expect(mockedApi.resume).toHaveBeenCalledWith(2));
    expect(notify.success).toHaveBeenCalledWith('サービスIDを再開しました');
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('追加ボタンで作成モーダルを開き、編集ボタンは詳細（version 持ち）を取り直してから編集モーダルを開くこと', async () => {
    mockedApi.get.mockResolvedValue(detail({ id: 1, version: 3 }));
    render(<ServiceIdentitiesPage />);
    await screen.findByText('夜間バッチ');

    expect(screen.queryByText('作成モーダル')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'サービスIDを追加' }));
    expect(screen.getByText('作成モーダル')).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[0]);
    expect(await screen.findByText('編集モーダル: 夜間バッチ')).toBeInTheDocument();
    expect(mockedApi.get).toHaveBeenCalledWith(1);
  });

  // 他管理者の店舗追加・削除に追随するため、目録はモーダルを開くたびに取り直す
  it('モーダルを開くと店舗目録を取り直すこと', async () => {
    render(<ServiceIdentitiesPage />);
    await screen.findByText('夜間バッチ');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: 'サービスIDを追加' }));

    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(2));
  });

  // 遅い応答が後から届いて選び直した対象を上書きすると、別のサービスIDの授権を編集してしまう
  it('編集を続けて押したとき、遅れて届いた前の詳細応答で対象を上書きしないこと', async () => {
    let resolveSlow: (value: ServiceIdentityResponse) => void = () => {};
    const slow = new Promise<ServiceIdentityResponse>(resolve => {
      resolveSlow = resolve;
    });
    mockedApi.get.mockImplementation(id =>
      id === 1 ? slow : Promise.resolve(detail({ id: 2, display_name: '外部連携', version: 1 }))
    );
    render(<ServiceIdentitiesPage />);
    await screen.findByText('夜間バッチ');

    const editButtons = screen.getAllByRole('button', { name: '編集' });
    fireEvent.click(editButtons[0]);
    fireEvent.click(editButtons[1]);
    expect(await screen.findByText('編集モーダル: 外部連携')).toBeInTheDocument();

    await act(async () => {
      resolveSlow(detail({ id: 1, version: 9 }));
    });

    expect(screen.getByText('編集モーダル: 外部連携')).toBeInTheDocument();
    expect(screen.queryByText('編集モーダル: 夜間バッチ')).not.toBeInTheDocument();
  });
});
