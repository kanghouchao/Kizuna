import { fireEvent, render, screen } from '@testing-library/react';
import { PlatformStaffResponse, platformAuthApi, platformStaffApi } from '@/entities/user';
import StaffPage from '../ui/StaffPage';

jest.mock('@/entities/user', () => ({
  platformStaffApi: { list: jest.fn() },
  platformAuthApi: { stores: jest.fn() },
}));

jest.mock('@/features/staff-management', () => {
  const React = require('react');
  return {
    StaffCreateModal: ({ open }: { open: boolean }) =>
      open ? React.createElement('div', null, '作成モーダル表示中') : null,
    StaffEditDrawer: ({
      open,
      staff,
    }: {
      open: boolean;
      staff: { display_name: string } | null;
    }) =>
      open ? React.createElement('div', null, `編集ドロワー:${staff?.display_name ?? ''}`) : null,
    bundleSetLabel: () => '権限束ラベル',
    storeSetLabel: () => '担当店舗ラベル',
  };
});

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedStaffApi = platformStaffApi as jest.Mocked<typeof platformStaffApi>;
const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;

const staff = (override: Partial<PlatformStaffResponse>): PlatformStaffResponse => ({
  id: 1,
  email: 'staff@example.com',
  display_name: '山田太郎',
  enabled: true,
  bundles: [],
  store_scope_type: 'ALL_STORES',
  store_ids: [],
  settlement_scope_type: null,
  settlement_store_ids: [],
  version: 0,
  ...override,
});

describe('スタッフ一覧ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.stores.mockResolvedValue([]);
    mockedStaffApi.list.mockResolvedValue([
      staff({ id: 1, display_name: '山田太郎', enabled: true }),
      staff({ id: 2, display_name: '鈴木花子', enabled: false }),
    ]);
  });

  it('氏名と在籍状態を一覧表示すること', async () => {
    render(<StaffPage />);

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('鈴木花子')).toBeInTheDocument();
    expect(screen.getByText('有効')).toBeInTheDocument();
    expect(screen.getByText('停止中')).toBeInTheDocument();
  });

  it('行クリックで対象スタッフの編集ドロワーが開くこと', async () => {
    render(<StaffPage />);

    fireEvent.click(await screen.findByText('鈴木花子'));

    expect(screen.getByText('編集ドロワー:鈴木花子')).toBeInTheDocument();
  });

  it('行内の編集ボタンでも対象スタッフの編集ドロワーが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[0]);

    expect(screen.getByText('編集ドロワー:山田太郎')).toBeInTheDocument();
  });

  it('スタッフを追加ボタンで作成モーダルが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getByRole('button', { name: 'スタッフを追加' }));

    expect(screen.getByText('作成モーダル表示中')).toBeInTheDocument();
  });
});

describe('スタッフ一覧ページの外殻', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.stores.mockResolvedValue([]);
    mockedStaffApi.list.mockResolvedValue([]);
  });

  it('見出し（h1）・副題を備え、主アクションが button ロールのままであること', async () => {
    const { container } = render(<StaffPage />);
    await screen.findByText('スタッフが登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: 'スタッフ管理' })).toBeInTheDocument();
    // 外殻の class 文字列は DESIGN.md が規格として定めた面そのもの。
    expect(container.firstElementChild).toHaveClass('space-y-6');
    expect(
      screen.getByText('権限束・担当店舗・精算範囲の付与と編集ができます。')
    ).toBeInTheDocument();
    // e2e（staff-management）は button ロールで取得するため、リンク化してはならない
    expect(screen.getByRole('button', { name: 'スタッフを追加' })).toBeInTheDocument();
  });
});
