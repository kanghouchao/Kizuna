import { act, fireEvent, render, screen } from '@testing-library/react';
import { downloadReport, type ReportCriteria } from '@/entities/operational-report';
import { ReportExport } from '../ReportExport';

jest.mock('@/entities/operational-report', () => ({
  ...jest.requireActual('@/entities/operational-report'),
  downloadReport: jest.fn(),
}));
const download = jest.mocked(downloadReport);
const criteria: ReportCriteria = {
  from: '2026-09-01',
  to: '2026-09-30',
  group_by: 'day',
  include_remuneration: true,
  include_advertising: true,
};
beforeEach(() => {
  jest.clearAllMocks();
  URL.createObjectURL = jest.fn(() => 'blob:report');
  URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});
afterEach(() => jest.restoreAllMocks());

test('repeated clicks generate one export with both applied amount sources', async () => {
  let complete!: (blob: Blob) => void;
  download.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        complete = resolve;
      })
  );
  render(<ReportExport scope="platform" criteria={criteria} />);
  const csv = screen.getByRole('button', { name: 'CSV 全件出力' });
  fireEvent.click(csv);
  fireEvent.click(csv);
  fireEvent.click(screen.getByRole('button', { name: 'Excel 全件出力' }));
  expect(download).toHaveBeenCalledTimes(1);
  expect(download).toHaveBeenCalledWith('platform', criteria, 'csv', expect.any(AbortSignal));
  expect(csv).toBeDisabled();
  await act(async () => complete(new Blob(['report'], { type: 'text/csv' })));
  expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1);
  expect(csv).not.toBeDisabled();
});

test.each<Partial<ReportCriteria>>([
  { include_remuneration: false },
  { include_advertising: false },
  { group_by: 'month' },
  { store_id: 2 },
  { from: '2026-08-01' },
])('applied criteria change aborts pending export and discards its file: %j', async change => {
  let complete!: (blob: Blob) => void;
  download.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        complete = resolve;
      })
  );
  const view = render(<ReportExport scope="platform" criteria={criteria} />);
  fireEvent.click(screen.getByRole('button', { name: 'CSV 全件出力' }));
  const signal = download.mock.calls[0][3];
  view.rerender(<ReportExport scope="platform" criteria={{ ...criteria, ...change }} />);
  expect(signal.aborted).toBe(true);
  await act(async () => complete(new Blob(['old'], { type: 'text/csv' })));
  expect(URL.createObjectURL).not.toHaveBeenCalled();
  expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
});

test('precision rejection remains visible and retries the same Excel criteria', async () => {
  download
    .mockRejectedValueOnce(new Error('金額がExcelの精度上限を超えています。'))
    .mockResolvedValueOnce(new Blob(['xlsx']));
  render(<ReportExport scope="store" criteria={criteria} />);
  fireEvent.click(screen.getByRole('button', { name: 'Excel 全件出力' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(
    '金額がExcelの精度上限を超えています。'
  );
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(download).toHaveBeenLastCalledWith('store', criteria, 'xlsx', expect.any(AbortSignal));
  await act(async () => {});
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
