import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { fetchReport, type OperationalReport } from '@/entities/operational-report';
import { ReportExport } from '@/features/operational-report-export';
import { readTokenClaims } from '@/shared/lib';
import StoreOperationalReportsPage from '../StoreOperationalReportsPage';

let mockStoreId = '1';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: mockStoreId }) }));
jest.mock('@/entities/operational-report', () => ({
  ...jest.requireActual('@/entities/operational-report'),
  fetchReport: jest.fn(),
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));
jest.mock('@/features/operational-report-export', () => ({
  ReportExport: jest.fn(() => <button>CSV 全件出力</button>),
}));
const fetch = jest.mocked(fetchReport);
function report(total: number): OperationalReport {
  return {
    from: '2026-09-01',
    to: '2026-09-30',
    group_by: 'day',
    generated_at: '2026-10-01T12:00:00+09:00',
    basis: 'completed-orders-current-v1',
    stores: [{ store_id: 1, store_name: '日本語店舗' }],
    total_order_count: 1,
    invalidated_order_count: 0,
    total_fee: total,
    total_remuneration: 7000,
    rows: {
      content: [],
      total_elements: 0,
      total_pages: 0,
      size: 20,
      number: 0,
      first: true,
      last: true,
      number_of_elements: 0,
      empty: true,
    },
  } as OperationalReport;
}
beforeEach(() => {
  jest.clearAllMocks();
  mockStoreId = '1';
  jest.mocked(readTokenClaims).mockReturnValue({
    authorities: ['PERM_ORDER_MANAGE', 'PERM_OPERATIONAL_REPORT_VIEW'],
    userType: 'STAFF',
    storeBridge: false,
  });
});
test('view permission does not show export and failures clear totals before retry', async () => {
  fetch
    .mockResolvedValueOnce(report(12000))
    .mockRejectedValueOnce(new Error('failed'))
    .mockResolvedValueOnce(report(16000));
  render(<StoreOperationalReportsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '照会' }));
  expect(await screen.findByLabelText('請求額（円）')).toHaveTextContent('12,000');
  expect(screen.queryByRole('button', { name: 'CSV 全件出力' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('集計を取得できませんでした');
  expect(screen.queryByLabelText('請求額（円）')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByLabelText('請求額（円）')).toHaveTextContent('16,000');
});
test('late response for previous criteria is discarded', async () => {
  let previous!: (value: OperationalReport) => void;
  fetch
    .mockImplementationOnce(
      () =>
        new Promise(resolve => {
          previous = resolve;
        })
    )
    .mockResolvedValueOnce(report(16000));
  render(<StoreOperationalReportsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '照会' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText('開始営業日'), { target: { value: '2026-09-01' } });
  fireEvent.change(screen.getByLabelText('終了営業日'), { target: { value: '2026-09-30' } });
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(await screen.findByLabelText('請求額（円）')).toHaveTextContent('16,000');
  await act(async () => previous(report(999)));
  expect(screen.getByLabelText('請求額（円）')).toHaveTextContent('16,000');
});
test('invalid period is shown next to fields without sending request', async () => {
  render(<StoreOperationalReportsPage />);
  fireEvent.change(await screen.findByLabelText('開始営業日'), { target: { value: '2026-02-30' } });
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(
    (await screen.findAllByText('正しい営業日を開始から終了の順で366日以内に入力してください'))
      .length
  ).toBeGreaterThan(0);
  expect(fetch).not.toHaveBeenCalled();
});

function grantRemuneration() {
  jest.mocked(readTokenClaims).mockReturnValue({
    authorities: [
      'PERM_ORDER_MANAGE',
      'PERM_OPERATIONAL_REPORT_VIEW',
      'PERM_OPERATIONAL_REPORT_EXPORT',
      'PERM_REMUNERATION_VIEW',
    ],
    userType: 'STAFF',
    storeBridge: true,
  });
}
function remunerationReport(): OperationalReport {
  const data = report(0);
  data.remuneration = {
    known_guarantee_total: 1200,
    guarantee_total: null,
    bonus_total: 0,
    total: null,
    pending_attendance_days: 2,
    not_configured_days: 1,
  };
  data.rows = {
    ...data.rows,
    content: [
      {
        store_id: 1,
        store_name: '日本語店舗',
        period: '2026-09-01',
        order_count: 0,
        invalidated_order_count: 0,
        total_fee: 0,
        total_remuneration: 0,
        remuneration: data.remuneration,
      },
    ],
    total_elements: 1,
    total_pages: 1,
  };
  return data;
}
test('legacy permission omits remuneration option and query parameter', async () => {
  fetch.mockResolvedValueOnce(report(12000));
  render(<StoreOperationalReportsPage />);
  expect(
    screen.queryByRole('checkbox', { name: '保証不足分・ボーナスを含める' })
  ).not.toBeInTheDocument();
  fireEvent.click(await screen.findByRole('button', { name: '照会' }));
  await screen.findByLabelText('請求額（円）');
  expect(fetch.mock.calls[0][1]).not.toHaveProperty('include_remuneration');
  expect(screen.queryByLabelText('報酬合計（円）')).not.toBeInTheDocument();
});
test('unknown totals, known subtotal and zero bonus are distinct in summary and no-order row', async () => {
  grantRemuneration();
  fetch.mockResolvedValueOnce(remunerationReport());
  render(<StoreOperationalReportsPage />);
  const checkbox = await screen.findByRole('checkbox', { name: '保証不足分・ボーナスを含める' });
  expect(checkbox).not.toBeChecked();
  fireEvent.click(checkbox);
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(await screen.findByLabelText('保証不足分（円）')).toHaveTextContent('未確定');
  expect(screen.getByLabelText('報酬合計（円）')).toHaveTextContent('未確定');
  expect(screen.getByLabelText('ボーナス（円）')).toHaveTextContent(/^0$/);
  expect(screen.getAllByText('保証不足分の既知小計: 1,200 円')).toHaveLength(2);
  expect(screen.getAllByText('出勤確認待ち: 2 人日 / 日額未設定: 1 人日')).toHaveLength(2);
  expect(fetch.mock.calls[0][1]).toHaveProperty('include_remuneration', true);
  fireEvent.click(checkbox);
  fireEvent.change(screen.getByLabelText('開始営業日'), { target: { value: '2026-08-01' } });
  expect(jest.mocked(ReportExport).mock.lastCall?.[0].criteria).toEqual(fetch.mock.calls[0][1]);
});
test('applying option change discards a late remuneration response', async () => {
  grantRemuneration();
  let previous!: (value: OperationalReport) => void;
  fetch
    .mockImplementationOnce(
      () =>
        new Promise(resolve => {
          previous = resolve;
        })
    )
    .mockResolvedValueOnce(report(16000));
  render(<StoreOperationalReportsPage />);
  const checkbox = await screen.findByRole('checkbox', { name: '保証不足分・ボーナスを含める' });
  fireEvent.click(checkbox);
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
  fireEvent.click(checkbox);
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(await screen.findByLabelText('請求額（円）')).toHaveTextContent('16,000');
  await act(async () => previous(remunerationReport()));
  expect(screen.queryByLabelText('報酬合計（円）')).not.toBeInTheDocument();
  expect(fetch.mock.calls[1][1]).not.toHaveProperty('include_remuneration');
});
test('switching store clears remuneration results and discards pending old-store response', async () => {
  grantRemuneration();
  let previous!: (value: OperationalReport) => void;
  fetch
    .mockImplementationOnce(
      () =>
        new Promise(resolve => {
          previous = resolve;
        })
    )
    .mockResolvedValueOnce(report(16000));
  const view = render(<StoreOperationalReportsPage />);
  fireEvent.click(await screen.findByRole('checkbox', { name: '保証不足分・ボーナスを含める' }));
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
  mockStoreId = '2';
  view.rerender(<StoreOperationalReportsPage />);
  expect(await screen.findByRole('checkbox')).not.toBeChecked();
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(await screen.findByLabelText('請求額（円）')).toHaveTextContent('16,000');
  await act(async () => previous(remunerationReport()));
  expect(screen.queryByLabelText('報酬合計（円）')).not.toBeInTheDocument();
});

test('known zero guarantee and total remain zero on repeated remuneration queries', async () => {
  grantRemuneration();
  const data = remunerationReport();
  data.remuneration = {
    known_guarantee_total: 0,
    guarantee_total: 0,
    bonus_total: 0,
    total: 0,
    pending_attendance_days: 0,
    not_configured_days: 0,
  };
  data.rows.content[0].remuneration = data.remuneration;
  fetch.mockResolvedValue(data);
  render(<StoreOperationalReportsPage />);
  fireEvent.click(await screen.findByRole('checkbox', { name: '保証不足分・ボーナスを含める' }));
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(await screen.findByLabelText('保証不足分（円）')).toHaveTextContent(/^0$/);
  expect(screen.getByLabelText('報酬合計（円）')).toHaveTextContent(/^0$/);
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(await screen.findByLabelText('報酬合計（円）')).toHaveTextContent(/^0$/);
  expect(fetch.mock.calls[0]).toEqual(fetch.mock.calls[1]);
});
