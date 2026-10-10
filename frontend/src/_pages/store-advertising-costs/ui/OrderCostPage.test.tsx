import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { advertisingApi, type OrderCostReport } from '../api/advertising';
import { OrderCostPage } from './OrderCostPage';
jest.mock('../api/advertising', () => ({
  ...jest.requireActual('../api/advertising'),
  advertisingApi: { orderCosts: jest.fn(), download: jest.fn() },
}));
function report(month = '2026-09', name = '既知零'): OrderCostReport {
  return {
    store_id: 1,
    month,
    month_version: 1,
    generated_at: '2026-10-09T00:00:00Z',
    basis: 'recorded-sales-cost-per-valid-order-v1',
    media_matching: 'EXACT_STORED_NAME',
    order_basis: 'VALID_COMPLETED_ORIGINAL_BUSINESS_DATE_INCLUDING_ZERO',
    rounding: 'HALF_UP_2_DECIMAL_YEN',
    cost_entry_count: 1,
    recorded_sales_amount: 0,
    valid_completed_order_count: 3,
    zero_amount_order_count: 1,
    unnamed_media_order_count: 1,
    rows: {
      content: [
        {
          media_name: name,
          cost_entry_count: 1,
          recorded_sales_amount: 0,
          valid_completed_order_count: 1,
          zero_amount_order_count: 1,
          cost_per_order: '0.00',
          calculation_status: 'CALCULATED',
        },
        {
          media_name: '費用なし',
          cost_entry_count: 0,
          recorded_sales_amount: null,
          valid_completed_order_count: 1,
          zero_amount_order_count: 0,
          cost_per_order: null,
          calculation_status: 'NO_COST_RECORDS',
        },
      ],
      number: 0,
      size: 20,
      total_pages: 1,
      total_elements: 2,
    },
  };
}
beforeEach(() => jest.clearAllMocks());
it('既知零・未登録・未算出と媒体未入力を区別する', async () => {
  jest.mocked(advertisingApi.orderCosts).mockResolvedValue(report());
  render(<OrderCostPage store="1" month="2026-09" canExport={false} />);
  expect(
    within(await screen.findByRole('row', { name: /^既知零/ })).getByText('約 0.00円')
  ).toBeVisible();
  const missing = screen.getByRole('row', { name: /^費用なし/ });
  expect(within(missing).getByText('未登録')).toBeVisible();
  expect(within(missing).getByText('未算出')).toBeVisible();
  expect(screen.getByLabelText('受注比較の月合計')).toHaveTextContent(
    '媒体未入力の有効完了受注：1件'
  );
  expect(screen.getByText(/新客単価は未提供/)).toBeVisible();
  expect(screen.queryByRole('button', { name: 'CSV 全件出力' })).not.toBeInTheDocument();
});
it('小数文字列を浮動小数へ変換せず表示する', async () => {
  const r = report();
  r.rows.content[0].cost_per_order = '9007199254740990.01';
  jest.mocked(advertisingApi.orderCosts).mockResolvedValue(r);
  render(<OrderCostPage store="1" month="2026-09" canExport={false} />);
  expect(await screen.findByText('約 9,007,199,254,740,990.01円')).toBeVisible();
});
it('ページ失敗で行と総計を消し、初頁から再試行する', async () => {
  const r = report();
  r.rows.total_pages = 2;
  r.rows.total_elements = 21;
  jest
    .mocked(advertisingApi.orderCosts)
    .mockResolvedValueOnce(r)
    .mockRejectedValueOnce(new Error('切断'))
    .mockResolvedValueOnce(report());
  render(<OrderCostPage store="1" month="2026-09" canExport />);
  await screen.findByRole('cell', { name: '既知零' });
  fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  const alert = await screen.findByRole('alert');
  expect(screen.queryByLabelText('受注比較の月合計')).not.toBeInTheDocument();
  expect(screen.queryByRole('cell', { name: '既知零' })).not.toBeInTheDocument();
  fireEvent.click(within(alert).getByRole('button'));
  await screen.findByRole('cell', { name: '既知零' });
  expect(advertisingApi.orderCosts).toHaveBeenLastCalledWith('2026-09', 0);
});
it('店舗と月の切替後に古い照会と遅延出力を反映しない', async () => {
  let finish!: (r: OrderCostReport) => void, finishExport!: (r: Blob) => void;
  jest
    .mocked(advertisingApi.orderCosts)
    .mockImplementationOnce(
      () =>
        new Promise(r => {
          finish = r;
        })
    )
    .mockResolvedValue(report('2026-10', '新月'));
  jest.mocked(advertisingApi.download).mockImplementation(
    () =>
      new Promise(r => {
        finishExport = r;
      })
  );
  const createUrl = jest.fn();
  URL.createObjectURL = createUrl;
  const view = render(<OrderCostPage key="1-09" store="1" month="2026-09" canExport />);
  await waitFor(() => expect(advertisingApi.orderCosts).toHaveBeenCalledTimes(1));
  const button = screen.getByRole('button', { name: 'CSV 全件出力' });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(advertisingApi.download).toHaveBeenCalledTimes(1);
  const [month, format, signal, kind] = jest.mocked(advertisingApi.download).mock.calls[0];
  expect([month, format, kind]).toEqual(['2026-09', 'csv', 'orders']);
  view.rerender(<OrderCostPage key="2-10" store="2" month="2026-10" canExport={false} />);
  expect(await screen.findByText('新月')).toBeVisible();
  expect(signal.aborted).toBe(true);
  await act(async () => {
    finish(report());
    finishExport(new Blob(['old'], { type: 'text/csv' }));
  });
  expect(screen.queryByRole('cell', { name: '既知零' })).not.toBeInTheDocument();
  expect(createUrl).not.toHaveBeenCalled();
});

it('前後の空白を持つ媒体を見分けられる', async () => {
  jest.mocked(advertisingApi.orderCosts).mockResolvedValue(report('2026-09', ' ABC '));
  render(<OrderCostPage store="1" month="2026-09" canExport={false} />);
  expect(await screen.findByText('前後に空白あり')).toBeVisible();
});
