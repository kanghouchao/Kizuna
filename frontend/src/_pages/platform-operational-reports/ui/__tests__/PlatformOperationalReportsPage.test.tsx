import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { fetchReport, type OperationalReport } from '@/entities/operational-report';
import { readTokenClaims } from '@/shared/lib';
import PlatformOperationalReportsPage from '../PlatformOperationalReportsPage';

jest.mock('@/entities/operational-report', () => ({
  ...jest.requireActual('@/entities/operational-report'),
  fetchReport: jest.fn(),
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));
jest.mock('@/features/operational-report-export', () => ({
  ReportExport: () => <button>CSV 全件出力</button>,
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
  jest.mocked(readTokenClaims).mockReturnValue({
    authorities: ['PERM_ORDER_SET_MANAGE', 'PERM_OPERATIONAL_REPORT_VIEW'],
    userType: 'STAFF',
    storeBridge: false,
  });
});
test('view permission does not show export and failures clear totals before retry', async () => {
  fetch
    .mockResolvedValueOnce(report(12000))
    .mockRejectedValueOnce(new Error('failed'))
    .mockResolvedValueOnce(report(16000));
  render(<PlatformOperationalReportsPage />);
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
  render(<PlatformOperationalReportsPage />);
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
  render(<PlatformOperationalReportsPage />);
  fireEvent.change(await screen.findByLabelText('開始営業日'), { target: { value: '2026-02-30' } });
  fireEvent.click(screen.getByRole('button', { name: '照会' }));
  expect(
    (await screen.findAllByText('正しい営業日を開始から終了の順で366日以内に入力してください'))
      .length
  ).toBeGreaterThan(0);
  expect(fetch).not.toHaveBeenCalled();
});

test.each([
  ['ADVERTISING_COST_VIEW', false, false],
  ['ADVERTISING_COST_SET_VIEW', false, true],
  ['ADVERTISING_COST_SET_VIEW', true, true],
] as const)(
  'platform permission %s export %s controls selected advertising',
  async (permission, exportAllowed, visible) => {
    jest.mocked(readTokenClaims).mockReturnValue({
      authorities: [
        'PERM_ORDER_SET_MANAGE',
        'PERM_OPERATIONAL_REPORT_VIEW',
        'PERM_OPERATIONAL_REPORT_EXPORT',
        `PERM_${permission}`,
        ...(exportAllowed
          ? ['PERM_ADVERTISING_COST_SET_EXPORT']
          : ['PERM_ADVERTISING_COST_EXPORT']),
      ],
      userType: 'STAFF',
      storeBridge: false,
    });
    const data = report(0);
    if (visible)
      data.advertising = {
        status: 'NO_RECORDS',
        entry_count: 0,
        sales_amount: 0,
        recruitment_amount: 0,
        recorded_total_amount: 0,
      };
    fetch.mockResolvedValue(data);
    render(<PlatformOperationalReportsPage />);
    const checkbox = screen.queryByRole('checkbox', { name: '広告費を含める' });
    if (visible) {
      expect(checkbox).not.toBeChecked();
      fireEvent.click(checkbox!);
    } else expect(checkbox).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('店舗ID（空欄は授権全店）'), { target: { value: '8' } });
    fireEvent.click(screen.getByRole('button', { name: '照会' }));
    await screen.findByLabelText('請求額（円）');
    expect(fetch.mock.calls[0][1]).toMatchObject({ store_id: 8 });
    expect(Boolean(fetch.mock.calls[0][1].include_advertising)).toBe(visible);
    if (!visible || exportAllowed)
      expect(screen.getByRole('button', { name: 'CSV 全件出力' })).toBeInTheDocument();
    else expect(screen.queryByRole('button', { name: 'CSV 全件出力' })).not.toBeInTheDocument();
  }
);
