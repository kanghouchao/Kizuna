import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
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
    StaffEditModal: ({ open, staff }: { open: boolean; staff: { display_name: string } | null }) =>
      open ? React.createElement('div', null, `編集モーダル:${staff?.display_name ?? ''}`) : null,
    roleSetLabel: () => 'ロールラベル',
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
  roles: [],
  store_scope_type: 'ALL_STORES',
  store_ids: [],
  version: 0,
  ...override,
});

const paginated = (
  rows: PlatformStaffResponse[],
  override: Partial<PageResult<PlatformStaffResponse>> = {}
): PageResult<PlatformStaffResponse> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
  ...override,
});

describe('スタッフ一覧ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.stores.mockResolvedValue([]);
    mockedStaffApi.list.mockResolvedValue(
      paginated([
        staff({ id: 1, display_name: '山田太郎', enabled: true }),
        staff({ id: 2, display_name: '鈴木花子', enabled: false }),
      ])
    );
  });

  it('氏名と在籍状態を一覧表示すること', async () => {
    render(<StaffPage />);

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('鈴木花子')).toBeInTheDocument();
    expect(screen.getByText('有効')).toBeInTheDocument();
    expect(screen.getByText('停止中')).toBeInTheDocument();
  });

  it('行クリックで対象スタッフの編集モーダルが開くこと', async () => {
    render(<StaffPage />);

    fireEvent.click(await screen.findByText('鈴木花子'));

    expect(screen.getByText('編集モーダル:鈴木花子')).toBeInTheDocument();
  });

  it('行内の編集ボタンでも対象スタッフの編集モーダルが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[0]);

    expect(screen.getByText('編集モーダル:山田太郎')).toBeInTheDocument();
  });

  it('スタッフを追加ボタンで作成モーダルが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getByRole('button', { name: 'スタッフを追加' }));

    expect(screen.getByText('作成モーダル表示中')).toBeInTheDocument();
  });

  it('検索は 0 起点の page/size/search のペイロードで再取得すること', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.change(screen.getByLabelText('スタッフを検索'), { target: { value: '山田' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: '山田',
      })
    );
  });

  // クリアは入力を空にすると同時に取り直す。検索語 state をそのまま読むと更新前の値で
  // 取得してしまうため、適用済み検索語は ref で持っている（その回帰を固定する）。
  it('クリアは検索語を空にして取り直すこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.change(screen.getByLabelText('スタッフを検索'), { target: { value: '山田' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));
    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({ page: 0, size: 10, search: '山田' })
    );

    fireEvent.click(screen.getByRole('button', { name: 'クリア' }));

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: undefined,
      })
    );
    expect(screen.getByLabelText('スタッフを検索')).toHaveValue('');
  });

  it('ページ番号のクリックで該当ページを取得すること', async () => {
    mockedStaffApi.list.mockImplementation(({ page }) =>
      Promise.resolve(
        paginated([staff({ id: 1, display_name: '山田太郎' })], { page, pageCount: 3, total: 25 })
      )
    );

    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 1,
        size: 10,
        search: undefined,
      })
    );
  });
});

describe('スタッフ一覧ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.stores.mockResolvedValue([]);
    mockedStaffApi.list.mockResolvedValue(paginated([]));
  });

  it('見出し（h1）・副題を備え、主アクションが button ロールのままであること', async () => {
    render(<StaffPage />);
    await screen.findByText('スタッフが登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: 'スタッフ管理' })).toBeInTheDocument();
    expect(screen.getByText('ロール・担当店舗の付与と編集ができます。')).toBeInTheDocument();
    // e2e（staff-management）は button ロールで取得するため、リンク化してはならない
    expect(screen.getByRole('button', { name: 'スタッフを追加' })).toBeInTheDocument();
  });
});
