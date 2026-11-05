import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { staffApi, useStoreContext, type StaffResponse } from '@/entities/user';
import { StaffEditPage } from '..';

const mockPush = jest.fn();
let mockRouteId = '42';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => ({ id: mockRouteId }),
}));

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  staffApi: { get: jest.fn(), update: jest.fn(), grantableRoles: jest.fn() },
  useStoreContext: jest.fn(),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedStaffApi = staffApi as jest.Mocked<typeof staffApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;
const mockedStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;

const staff = (override: Partial<StaffResponse> = {}): StaffResponse => ({
  id: 42,
  email: 'yamada@example.com',
  display_name: '山田太郎',
  enabled: true,
  roles: [{ id: 10, name: '店長' }],
  store_scope_type: 'SPECIFIC_STORES',
  store_ids: [1],
  version: 7,
  editable: true,
  ...override,
});

describe('従業員の権限編集ページ', () => {
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
    mockedStaffApi.grantableRoles.mockResolvedValue([
      {
        id: 10,
        name: '店長',
        system: true,
        permissions: [{ code: 'ORDER_MANAGE', label: '受注の管理' }],
      },
      {
        id: 11,
        name: '経理',
        system: false,
        permissions: [{ code: 'ADVERTISING_COST_MANAGE', label: '広告費の登録・更新' }],
      },
    ]);
    mockedStaffApi.get.mockResolvedValue(staff());
    mockedStaffApi.update.mockResolvedValue(staff({ version: 8 }));
  });

  it('id で従業員を取得し、現在の授権で欄を初期化する', async () => {
    render(<StaffEditPage />);

    expect(
      await screen.findByRole('heading', { level: 1, name: '山田太郎 の権限を編集' })
    ).toBeInTheDocument();
    expect(mockedStaffApi.get).toHaveBeenCalledWith(42);
    expect(screen.getByText('yamada@example.com')).toBeInTheDocument();
    expect(await screen.findByRole('checkbox', { name: '店長' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: '経理' })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: '個別店舗' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: '新宿店' })).toBeChecked();
    expect(screen.queryByLabelText('メールアドレス')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'この設定の結果' })).toHaveTextContent(
      '山田太郎さんは 店長 として 新宿店 のデータにアクセスできます'
    );
  });

  it('保存は取得した version を往復し、成功したら一覧へ戻る', async () => {
    render(<StaffEditPage />);
    fireEvent.click(await screen.findByRole('checkbox', { name: '経理' }));
    fireEvent.click(screen.getByRole('radio', { name: '全店舗' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/staff'));
    expect(mockedStaffApi.update).toHaveBeenCalledWith(42, {
      role_ids: [10, 11],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
      version: 7,
    });
    expect(mockedNotify.success).toHaveBeenCalledWith('権限を更新しました');
  });

  it('応答の店舗範囲が欠落しても全店舗へ拡大しない', async () => {
    mockedStaffApi.get.mockResolvedValue(
      staff({ store_scope_type: undefined, store_ids: undefined })
    );
    render(<StaffEditPage />);

    expect(await screen.findByRole('radio', { name: '個別店舗' })).toBeChecked();
    expect(screen.getByRole('radio', { name: '全店舗' })).not.toBeChecked();
  });

  it('409 は入力を残して案内を出し、読み込み直すと最新の授権と version で描き直す', async () => {
    mockedStaffApi.update.mockRejectedValueOnce({ response: { status: 409, data: {} } });
    render(<StaffEditPage />);
    fireEvent.click(await screen.findByRole('checkbox', { name: '経理' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    const banner = await screen.findByText(/他の担当者が更新しました/);
    expect(screen.getByRole('checkbox', { name: '経理' })).toBeChecked();
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled();
    expect(mockPush).not.toHaveBeenCalled();

    mockedStaffApi.get.mockResolvedValue(staff({ roles: [{ id: 11, name: '経理' }], version: 9 }));
    fireEvent.click(
      within(banner.closest('[role="alert"]') as HTMLElement).getByRole('button', {
        name: '最新の内容を読み込む',
      })
    );

    await waitFor(() => expect(screen.getByRole('checkbox', { name: '店長' })).not.toBeChecked());
    expect(screen.getByRole('checkbox', { name: '経理' })).toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));
    await waitFor(() =>
      expect(mockedStaffApi.update).toHaveBeenLastCalledWith(
        42,
        expect.objectContaining({ version: 9 })
      )
    );
  });

  it('見つからない従業員は一覧への導線だけを出す', async () => {
    mockedStaffApi.get.mockRejectedValue({ response: { status: 404 } });
    render(<StaffEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('この従業員は見つかりませんでした')).toBeInTheDocument();
    expect(within(region).getByRole('link', { name: '従業員一覧へ' })).toHaveAttribute(
      'href',
      '/admin/staff'
    );
  });

  it('数字でない id は取得せずに見つからない扱いにする', async () => {
    mockRouteId = 'abc';
    render(<StaffEditPage />);

    expect(await screen.findByText('この従業員は見つかりませんでした')).toBeInTheDocument();
    expect(mockedStaffApi.get).not.toHaveBeenCalled();
  });

  it('権限が及ばない従業員はフォームを出さずにその旨を述べる', async () => {
    mockedStaffApi.get.mockResolvedValue(staff({ editable: false }));
    render(<StaffEditPage />);

    expect(
      await screen.findByText('この従業員の権限は、あなたの権限では編集できません')
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();
  });

  it('取得に失敗したら再試行できる', async () => {
    mockedStaffApi.get.mockRejectedValueOnce(new Error('network'));
    render(<StaffEditPage />);

    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: '山田太郎 の権限を編集' })
    ).toBeInTheDocument();
  });
});
