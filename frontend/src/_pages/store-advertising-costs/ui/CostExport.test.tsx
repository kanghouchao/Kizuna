import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { advertisingApi } from '../api/advertising';
import { CostExport } from './CostExport';

jest.mock('../api/advertising', () => ({
  ...jest.requireActual('../api/advertising'),
  advertisingApi: { download: jest.fn() },
}));
const download = jest.mocked(advertisingApi.download);
let click: jest.SpyInstance;
beforeEach(() => {
  URL.createObjectURL = jest.fn(() => 'blob:cost');
  URL.revokeObjectURL = jest.fn();
  click = jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});
afterEach(() => jest.restoreAllMocks());

it('選んだ形式で出力し、店舗と月を含むファイル名で保存する', async () => {
  download.mockResolvedValue(new Blob(['x']));
  render(<CostExport store="1" month="2026-09" kind="media" />);
  fireEvent.click(screen.getByRole('button', { name: 'Excel 全件出力' }));
  await waitFor(() => expect(click).toHaveBeenCalledTimes(1));
  expect(download).toHaveBeenCalledWith('2026-09', 'xlsx', expect.any(AbortSignal), 'media');
  expect(click.mock.contexts[0]).toMatchObject({
    download: 'advertising-media-summaries-1-2026-09.xlsx',
  });
});

it('失敗はその場にサーバの文言で示し、再試行は同じ形式で出力し直す', async () => {
  download.mockRejectedValueOnce(new Error('対象月の広告費がありません'));
  download.mockResolvedValueOnce(new Blob(['x']));
  render(<CostExport store="1" month="2026-09" />);
  fireEvent.click(screen.getByRole('button', { name: 'CSV 全件出力' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('対象月の広告費がありません');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  await waitFor(() => expect(click).toHaveBeenCalledTimes(1));
  expect(download).toHaveBeenLastCalledWith('2026-09', 'csv', expect.any(AbortSignal), 'costs');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
