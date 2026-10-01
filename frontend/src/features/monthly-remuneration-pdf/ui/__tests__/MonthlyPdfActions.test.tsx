import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { fetchMonthlyPdf } from '@/entities/order';
import { MonthlyPdfActions } from '../MonthlyPdfActions';

jest.mock('@/entities/order', () => ({ fetchMonthlyPdf: jest.fn() }));
const fetchPdf = jest.mocked(fetchMonthlyPdf);
const criteria = { scope: 'self' as const, storeId: 1, month: '2026-09' };

beforeEach(() => {
  jest.clearAllMocks();
  URL.createObjectURL = jest.fn(() => 'blob:monthly-pdf');
  URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});
afterEach(() => jest.restoreAllMocks());

it('適用済み条件だけで取得しダウンロードする', async () => {
  fetchPdf.mockResolvedValue(new Blob(['pdf'], { type: 'application/pdf' }));
  render(<MonthlyPdfActions criteria={criteria} />);
  fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
  await screen.findByRole('link', { name: '保存する' });
  expect(fetchPdf).toHaveBeenCalledWith(criteria, expect.any(AbortSignal));
  expect(screen.getByRole('link', { name: '保存する' })).toHaveAttribute(
    'download',
    'monthly-remuneration-2026-09.pdf'
  );
});

it('中止した要求が遅れて成功しても成果物を開かず再試行できる', async () => {
  let finish!: (value: Blob) => void;
  fetchPdf.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      })
  );
  render(<MonthlyPdfActions criteria={criteria} />);
  fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
  const signal = fetchPdf.mock.calls[0][1];
  fireEvent.click(screen.getByRole('button', { name: '取得を中止' }));
  expect(signal.aborted).toBe(true);
  await act(async () => finish(new Blob(['old'])));
  expect(URL.createObjectURL).not.toHaveBeenCalled();
  fetchPdf.mockResolvedValue(new Blob(['new'], { type: 'application/pdf' }));
  fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
  await screen.findByRole('link', { name: '保存する' });
});

it('対象変更で古い要求を中止し旧対象を表示しない', async () => {
  let finish!: (value: Blob) => void;
  fetchPdf.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      })
  );
  const { rerender } = render(<MonthlyPdfActions criteria={criteria} />);
  fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
  rerender(<MonthlyPdfActions criteria={{ ...criteria, storeId: 2 }} />);
  expect(fetchPdf.mock.calls[0][1].aborted).toBe(true);
  await act(async () => finish(new Blob(['old'])));
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it('失敗を領域内で示し再試行で新しく生成する', async () => {
  fetchPdf.mockRejectedValueOnce(new Error('PDF を生成中です。'));
  fetchPdf.mockResolvedValue(new Blob(['pdf'], { type: 'application/pdf' }));
  render(<MonthlyPdfActions criteria={criteria} />);
  fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('PDF を生成中です。');
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  await screen.findByRole('link', { name: '保存する' });
  expect(fetchPdf).toHaveBeenCalledTimes(2);
});

it('印刷は取得したPDFを開きポップアップ拒否でも保存リンクを残す', async () => {
  jest.spyOn(window, 'open').mockReturnValue(null);
  fetchPdf.mockResolvedValue(new Blob(['pdf'], { type: 'application/pdf' }));
  const { unmount } = render(<MonthlyPdfActions criteria={criteria} />);
  fireEvent.click(screen.getByRole('button', { name: 'PDFを開いて印刷' }));
  await waitFor(() =>
    expect(screen.getByRole('link', { name: '生成した PDF を開く' })).toHaveAttribute(
      'href',
      'blob:monthly-pdf'
    )
  );
  unmount();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:monthly-pdf');
});
