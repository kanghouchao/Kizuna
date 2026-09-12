import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
import { StoreStaffResponse, storeStaffApi, useStoreContext } from '@/entities/user';
import StoreStaffPage from '../ui/StoreStaffPage';

jest.mock('@/entities/user', () => ({
  storeStaffApi: { list: jest.fn(), get: jest.fn() },
  useStoreContext: jest.fn(),
}));

// モーダルは開くまで mount されないため、mock は mount = 表示として描画する
jest.mock('@/features/staff-management', () => {
  const React = require('react');
  return {
    StoreStaffCreateModal: () => React.createElement('div', null, '作成モーダル表示中'),
    StoreStaffEditModal: ({
      resource,
    }: {
      resource: import('@/shared/lib').KeyedResource<StoreStaffResponse>;
    }) =>
      React.createElement(
        'div',
        null,
        `編集モーダル:${resource.data?.display_name ?? '読み込み中'}`,
        React.createElement('button', { onClick: () => void resource.reload() }, '競合再取得')
      ),
    roleSetLabel: () => 'ロールラベル',
  };
});

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = storeStaffApi as jest.Mocked<typeof storeStaffApi>;
const mockedStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;

const staff = (override: Partial<StoreStaffResponse>): StoreStaffResponse => ({
  id: 1,
  email: 'clerk@example.com',
  display_name: '山田太郎',
  enabled: true,
  roles: [],
  store_scope_type: 'SPECIFIC_STORES',
  store_ids: [1],
  version: 0,
  editable: true,
  ...override,
});

const paginated = (rows: StoreStaffResponse[]): PageResult<StoreStaffResponse> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
});

beforeEach(() => {
  jest.clearAllMocks();
  mockedStoreContext.mockReturnValue({
    stores: [{ id: 1, name: '本店' }],
    storeBridge: true,
    currentStoreId: '1',
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  });
});

describe('StoreStaffPage', () => {
  // 編集導線の有無はサーバの editable が唯一の根拠。前端が境界を再判定しないことの固定。
  it('editable=false の行には編集ボタンを出さず、権限が及ばないことを名乗る', async () => {
    mockedApi.list.mockResolvedValue(
      paginated([
        staff({ id: 1, display_name: '編集できる人', editable: true }),
        staff({ id: 2, email: 'peer@example.com', display_name: '同僚店長', editable: false }),
      ])
    );

    render(<StoreStaffPage />);

    await screen.findByText('編集できる人');
    expect(screen.getAllByRole('button', { name: '編集' })).toHaveLength(1);
    expect(screen.getByText('編集権限なし')).toBeInTheDocument();
  });

  it('編集ボタンから対象を渡して編集モーダルを開く', async () => {
    mockedApi.list.mockResolvedValue(paginated([staff({ display_name: '編集できる人' })]));
    mockedApi.get.mockResolvedValue(staff({ display_name: '編集できる人' }));

    render(<StoreStaffPage />);

    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    expect(await screen.findByText('編集モーダル:編集できる人')).toBeInTheDocument();
  });

  it('一覧の取得に失敗したら再試行導線つきで失敗を名乗る', async () => {
    mockedApi.list.mockRejectedValue(new Error('boom'));

    render(<StoreStaffPage />);

    expect(await screen.findByText('スタッフ一覧の取得に失敗しました')).toBeInTheDocument();
  });
});

test('同じ ID の連続競合再取得では最後に開始した応答だけを採用する', async () => {
  let resolveOld!: (value: StoreStaffResponse) => void;
  mockedApi.list.mockResolvedValue(paginated([staff({ id: 1, display_name: '一覧' })]));
  mockedApi.get
    .mockResolvedValueOnce(staff({ id: 1, display_name: '初期' }))
    .mockReturnValueOnce(
      new Promise(done => {
        resolveOld = done;
      })
    )
    .mockResolvedValueOnce(staff({ id: 1, display_name: '最新', version: 3 }));
  render(<StoreStaffPage />);
  fireEvent.click(await screen.findByRole('button', { name: '編集' }));
  await screen.findByText('編集モーダル:初期');
  fireEvent.click(screen.getByRole('button', { name: '競合再取得' }));
  fireEvent.click(screen.getByRole('button', { name: '競合再取得' }));
  await screen.findByText('編集モーダル:最新');
  await act(async () => resolveOld(staff({ id: 1, display_name: '古い応答', version: 2 })));
  expect(screen.getByText('編集モーダル:最新')).toBeInTheDocument();
  expect(screen.queryByText('編集モーダル:古い応答')).not.toBeInTheDocument();
});
