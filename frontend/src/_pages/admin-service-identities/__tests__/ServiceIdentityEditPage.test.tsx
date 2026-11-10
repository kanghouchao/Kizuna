import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { serviceIdentityApi, useStoreContext, type ServiceIdentityResponse } from '@/entities/user';
import { ServiceIdentityEditPage } from '..';

const mockPush = jest.fn();
let mockRouteId = '42';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => ({ id: mockRouteId }),
}));

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  serviceIdentityApi: { get: jest.fn(), update: jest.fn(), grantableRoles: jest.fn() },
  useStoreContext: jest.fn(),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = serviceIdentityApi as jest.Mocked<typeof serviceIdentityApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;
const mockedStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;

const identity = (override: Partial<ServiceIdentityResponse> = {}): ServiceIdentityResponse => ({
  id: 42,
  display_name: '夜間失効',
  enabled: true,
  roles: [{ id: 20, name: '失効バッチ' }],
  store_scope_type: 'SPECIFIC_STORES',
  store_ids: [1],
  version: 7,
  ...override,
});

describe('サービスIDの権限編集ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRouteId = '42';
    mockedStoreContext.mockReturnValue({
      stores: [
        { id: 1, name: '新宿店' },
        { id: 2, name: '渋谷店' },
      ],
      currentStoreId: undefined,
      loadFailed: false,
      reload: jest.fn(),
      switchStore: jest.fn(),
    } as unknown as ReturnType<typeof useStoreContext>);
    mockedApi.grantableRoles.mockResolvedValue([
      { id: 20, name: '失効バッチ', system: false, permission_count: 3 },
      { id: 21, name: '集計連携', system: false, permission_count: 1 },
    ]);
    mockedApi.get.mockResolvedValue(identity());
    mockedApi.update.mockResolvedValue(identity({ version: 8 }));
  });

  it('id でサービスIDを取得し、現在の授権で欄を初期化する', async () => {
    render(<ServiceIdentityEditPage />);

    expect(
      await screen.findByRole('heading', { level: 1, name: '夜間失効 の権限を編集' })
    ).toBeInTheDocument();
    expect(mockedApi.get).toHaveBeenCalledWith(42);
    expect(await screen.findByRole('checkbox', { name: '失効バッチ' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: '集計連携' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: '新宿店' })).toBeChecked();
    expect(screen.queryByLabelText('用途名')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'この設定の結果' })).toHaveTextContent(
      '夜間失効は 失効バッチ として 新宿店 のデータにアクセスできます'
    );
  });

  it('保存は取得した version を往復し、成功したら一覧へ戻る', async () => {
    render(<ServiceIdentityEditPage />);
    fireEvent.click(await screen.findByRole('checkbox', { name: '集計連携' }));
    fireEvent.click(screen.getByRole('button', { name: 'すべて選択' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/service-identities'));
    expect(mockedApi.update).toHaveBeenCalledWith(42, {
      role_ids: [20, 21],
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [1, 2],
      version: 7,
    });
    expect(mockedNotify.success).toHaveBeenCalledWith('権限を更新しました');
  });

  it('応答の店舗範囲が欠落しても全店舗へ拡大しない', async () => {
    mockedApi.get.mockResolvedValue(
      identity({ store_scope_type: undefined, store_ids: undefined })
    );
    render(<ServiceIdentityEditPage />);

    expect(await screen.findByRole('checkbox', { name: '新宿店' })).not.toBeChecked();
    expect(screen.queryByText(/現在は全店舗/)).not.toBeInTheDocument();
  });

  it('全店舗の授権はロールだけの変更では保つ', async () => {
    mockedApi.get.mockResolvedValue(identity({ store_scope_type: 'ALL_STORES', store_ids: [] }));
    render(<ServiceIdentityEditPage />);
    expect(await screen.findByText(/現在は全店舗/)).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('checkbox', { name: '集計連携' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/service-identities'));
    expect(mockedApi.update).toHaveBeenLastCalledWith(42, {
      role_ids: [20, 21],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
      version: 7,
    });
  });

  it('409 は入力を残して案内を出し、読み込み直すと最新の授権と version で描き直す', async () => {
    mockedApi.update.mockRejectedValueOnce({ response: { status: 409, data: {} } });
    render(<ServiceIdentityEditPage />);
    fireEvent.click(await screen.findByRole('checkbox', { name: '集計連携' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    const banner = await screen.findByText(/他の担当者が更新しました/);
    expect(screen.getByRole('checkbox', { name: '集計連携' })).toBeChecked();
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled();
    expect(mockPush).not.toHaveBeenCalled();

    mockedApi.get.mockResolvedValue(
      identity({ roles: [{ id: 21, name: '集計連携' }], version: 9 })
    );
    fireEvent.click(
      within(banner.closest('[role="alert"]') as HTMLElement).getByRole('button', {
        name: '最新の内容を読み込む',
      })
    );

    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: '失効バッチ' })).not.toBeChecked()
    );
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));
    await waitFor(() =>
      expect(mockedApi.update).toHaveBeenLastCalledWith(42, expect.objectContaining({ version: 9 }))
    );
  });

  it('見つからないサービスIDは一覧への導線だけを出す', async () => {
    mockedApi.get.mockRejectedValue({ response: { status: 404 } });
    render(<ServiceIdentityEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('このサービスIDは見つかりませんでした')).toBeInTheDocument();
    expect(within(region).getByRole('link', { name: 'サービスID一覧へ' })).toHaveAttribute(
      'href',
      '/admin/service-identities'
    );
  });

  it('数字でない id は取得せずに見つからない扱いにする', async () => {
    mockRouteId = 'abc';
    render(<ServiceIdentityEditPage />);

    expect(await screen.findByText('このサービスIDは見つかりませんでした')).toBeInTheDocument();
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it('取得に失敗したら再試行できる', async () => {
    mockedApi.get.mockRejectedValueOnce(new Error('network'));
    render(<ServiceIdentityEditPage />);

    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: '夜間失効 の権限を編集' })
    ).toBeInTheDocument();
  });
});
