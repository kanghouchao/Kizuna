import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CastMonthlyRemunerationsPage } from '../CastMonthlyRemunerationsPage';
import { selfMonthlyRemunerationApi, selfRemunerationApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  selfMonthlyRemunerationApi: { stores: jest.fn(), monthly: jest.fn() },
  selfRemunerationApi: { detail: jest.fn(), changes: jest.fn() },
}));

const store = { store_id: 1, store_name: '退店店舗' };
const order = {
  order_id: 'old-order',
  business_date: '2026-09-30',
  service_summary: '過去のコース',
  accrued_remuneration: 7000,
  completion_invalidated: false,
};
const statement = {
  ...store,
  month: '2026-09',
  total_remuneration: 14000,
  orders: { content: [order], number: 0, size: 1, total_elements: 2, total_pages: 2 },
};

beforeEach(() => {
  jest.resetAllMocks();
  jest
    .mocked(selfMonthlyRemunerationApi.stores)
    .mockResolvedValue({ rows: [store], page: 0, pageCount: 1, total: 1 });
  jest.mocked(selfMonthlyRemunerationApi.monthly).mockResolvedValue(statement);
  jest.mocked(selfRemunerationApi.detail).mockResolvedValue({
    order_id: order.order_id,
    enrollment_id: 'withdrawn',
    store_id: 1,
    store_name: store.store_name,
    business_date: order.business_date,
    completed_at: '2026-10-01T12:00:00Z',
    status: 'COMPLETED',
    completion_invalidated: false,
    agreed_remuneration: 7000,
    planned_remuneration: 0,
    accrued_remuneration: 7000,
    version: 1,
    items: [],
  });
  jest.mocked(selfRemunerationApi.changes).mockResolvedValue({ rows: [], nextCursor: null });
});

async function chooseStore(name = store.store_name) {
  fireEvent.click(await screen.findByRole('combobox', { name: '店舗' }));
  const option = await screen.findByRole('option', { name });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

async function search() {
  await chooseStore();
  fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-09' } });
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await screen.findByLabelText('月次報酬合計');
}

it('退店店舗の月合計を保ちながら適用済み条件でページを送る', async () => {
  render(<CastMonthlyRemunerationsPage />);
  await search();
  expect(screen.getByLabelText('月次報酬合計')).toHaveTextContent('¥14,000');
  fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-10' } });
  fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  await waitFor(() =>
    expect(selfMonthlyRemunerationApi.monthly).toHaveBeenLastCalledWith(1, '2026-09', 1)
  );
  await screen.findByText('過去のコース');
  expect(screen.getByLabelText('月次報酬合計')).toHaveTextContent('¥14,000');
});

it('本人詳細と履歴から戻っても店舗と月と合計を維持する', async () => {
  render(<CastMonthlyRemunerationsPage />);
  await search();
  fireEvent.click(screen.getByRole('button', { name: '詳細・変更履歴を確認' }));
  fireEvent.click(await screen.findByRole('button', { name: '変更履歴を確認' }));
  await screen.findByText('変更履歴はありません');
  expect(selfRemunerationApi.detail).toHaveBeenCalledWith('old-order');
  expect(selfRemunerationApi.changes).toHaveBeenCalledWith('old-order', undefined);
  fireEvent.click(screen.getByRole('button', { name: '一覧へ戻る' }));
  expect(screen.getByLabelText('対象月')).toHaveValue('2026-09');
  expect(screen.getByLabelText('月次報酬合計')).toHaveTextContent('¥14,000');
  expect(await screen.findByRole('combobox', { name: '店舗' })).toHaveTextContent('退店店舗');
});

it('再照会失敗では古い金額を消し同じ条件で再試行する', async () => {
  jest
    .mocked(selfMonthlyRemunerationApi.monthly)
    .mockResolvedValueOnce(statement)
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValue(statement);
  render(<CastMonthlyRemunerationsPage />);
  await search();
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await screen.findByText('月次給与明細を取得できませんでした。');
  expect(screen.queryByLabelText('月次報酬合計')).not.toBeInTheDocument();
  expect(screen.queryByText('過去のコース')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  await screen.findByLabelText('月次報酬合計');
  expect(selfMonthlyRemunerationApi.monthly).toHaveBeenLastCalledWith(1, '2026-09', 0);
});

it('空月は金額ゼロと説明を返し不可視の店舗は戻り先を示す', async () => {
  jest
    .mocked(selfMonthlyRemunerationApi.monthly)
    .mockResolvedValueOnce({
      ...statement,
      total_remuneration: 0,
      orders: { ...statement.orders, content: [], total_elements: 0, total_pages: 0 },
    })
    .mockRejectedValueOnce({ response: { status: 404 } });
  render(<CastMonthlyRemunerationsPage />);
  await search();
  expect(screen.getByLabelText('月次報酬合計')).toHaveTextContent('¥0');
  expect(screen.getByText('対象月の完了受注はありません')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await screen.findByText('対象の店舗が見つからないか、閲覧範囲外です。');
  expect(screen.getByRole('link', { name: '報酬一覧へ戻る' })).toHaveAttribute(
    'href',
    '/cast/remunerations'
  );
});

it('店舗候補は取得失敗から回復し次のページでも選択できる', async () => {
  const nextStore = { store_id: 2, store_name: '停止中の店舗' };
  jest
    .mocked(selfMonthlyRemunerationApi.stores)
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ rows: [store], page: 0, pageCount: 2, total: 2 })
    .mockResolvedValueOnce({ rows: [nextStore], page: 1, pageCount: 2, total: 2 });
  render(<CastMonthlyRemunerationsPage />);
  await screen.findByText('店舗を取得できませんでした。');
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  fireEvent.click(await screen.findByRole('button', { name: '店舗候補の次のページ' }));
  await chooseStore(nextStore.store_name);
  fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-09' } });
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await waitFor(() =>
    expect(selfMonthlyRemunerationApi.monthly).toHaveBeenLastCalledWith(2, '2026-09', 0)
  );
});

it('店舗未選択と不正な月は関連付けた入力エラーを示す', async () => {
  render(<CastMonthlyRemunerationsPage />);
  await screen.findByRole('combobox', { name: '店舗' });
  fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '0000-01' } });
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await screen.findByText('店舗を選択してください');
  await screen.findByText('対象月は YYYY-MM 形式で入力してください');
  expect(selfMonthlyRemunerationApi.monthly).not.toHaveBeenCalled();
});
