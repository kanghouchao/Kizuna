import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { advertisingApi, type MediaReport } from '../api/advertising';
import { MediaSummaryPage } from './MediaSummaryPage';
jest.mock('../api/advertising', () => ({
  ...jest.requireActual('../api/advertising'),
  advertisingApi: { media: jest.fn(), download: jest.fn() },
}));
function report(month = '2026-09', name = '媒体', count: number | null = 0): MediaReport {
  return {
    store_id: 1,
    month,
    month_version: 1,
    generated_at: '2026-10-09T00:00:00Z',
    basis: 'advertising-media-records-v1',
    media_matching: 'EXACT_STORED_NAME',
    inquiry_basis: 'MANUAL_RECORDED_SUM_NOT_DEDUPLICATED',
    entry_count: 2,
    recorded_total_amount: 300,
    category_totals: [
      { category: 'SALES', entry_count: 2, recorded_amount: 300 },
      { category: 'RECRUITMENT', entry_count: 0, recorded_amount: 0 },
    ],
    rows: {
      content: [
        {
          category: 'SALES',
          media_name: name,
          entry_count: 2,
          recorded_amount: 300,
          recorded_inquiry_entry_count: count === null ? 0 : 1,
          unrecorded_inquiry_entry_count: count === null ? 2 : 1,
          recorded_inquiry_count_sum: count,
          inquiry_status: count === null ? 'UNRECORDED' : 'PARTIAL',
        },
      ],
      number: 0,
      size: 20,
      total_pages: 1,
      total_elements: 1,
    },
  };
}
beforeEach(() => jest.clearAllMocks());
it('既知の零と部分未入力を区別し、人数の限定と月の全額を示す', async () => {
  jest.mocked(advertisingApi.media).mockResolvedValue(report());
  render(<MediaSummaryPage store="1" month="2026-09" canExport={false} />);
  const row = await screen.findByRole('row', { name: /^営業広告 媒体/ });
  expect(within(row).getByText('0人')).toBeVisible();
  expect(within(row).getByText('一部未入力')).toBeVisible();
  expect(within(row).getByText('入力済み 1行 ／ 未入力 1行')).toBeVisible();
  expect(screen.getByText(/入力済み人数の合計（重複未除外）/)).toBeVisible();
  expect(screen.queryByRole('button', { name: 'CSV 全件出力' })).not.toBeInTheDocument();
});
it('全行未入力を零にせず、遅れて返る旧月の応答を破棄する', async () => {
  let finish!: (value: MediaReport) => void;
  jest
    .mocked(advertisingApi.media)
    .mockImplementationOnce(
      () =>
        new Promise(r => {
          finish = r;
        })
    )
    .mockResolvedValueOnce(report('2026-10', '新月', null));
  const view = render(<MediaSummaryPage key="09" store="1" month="2026-09" canExport={false} />);
  await waitFor(() => expect(advertisingApi.media).toHaveBeenCalledTimes(1));
  view.rerender(<MediaSummaryPage key="10" store="1" month="2026-10" canExport={false} />);
  expect(await screen.findByText('新月')).toBeVisible();
  expect(screen.getByText('全行未入力')).toBeVisible();
  await act(async () => finish(report('2026-09', '旧月', 100)));
  expect(screen.queryByText('旧月')).not.toBeInTheDocument();
  expect(screen.queryByText('0人')).not.toBeInTheDocument();
});
it('通信失敗で古い値を残さず再試行する', async () => {
  jest
    .mocked(advertisingApi.media)
    .mockRejectedValueOnce(new Error('通信失敗'))
    .mockResolvedValueOnce(report());
  render(<MediaSummaryPage store="1" month="2026-09" canExport={true} />);
  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent('媒体別集計を取得できませんでした。');
  fireEvent.click(within(alert).getByRole('button'));
  expect(await screen.findByRole('cell', { name: '媒体' })).toBeVisible();
});

it('出力の連打を一要求にまとめ、切月後の古いファイルを保存しない', async () => {
  jest.mocked(advertisingApi.media).mockResolvedValue(report());
  let finish!: (value: Blob) => void;
  jest.mocked(advertisingApi.download).mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      })
  );
  const createUrl = jest.fn();
  URL.createObjectURL = createUrl;
  const view = render(<MediaSummaryPage key="09" store="1" month="2026-09" canExport />);
  const button = screen.getByRole('button', { name: 'CSV 全件出力' });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(advertisingApi.download).toHaveBeenCalledTimes(1);
  const [month, format, signal, media] = jest.mocked(advertisingApi.download).mock.calls[0];
  expect([month, format, media]).toEqual(['2026-09', 'csv', true]);
  view.rerender(<MediaSummaryPage key="10" store="1" month="2026-10" canExport />);
  expect(signal.aborted).toBe(true);
  await act(async () => finish(new Blob(['old'], { type: 'text/csv' })));
  expect(createUrl).not.toHaveBeenCalled();
});

it('空になった二ページ目を月全体の未登録と表示しない', async () => {
  const first = report();
  first.rows.total_pages = 2;
  first.rows.total_elements = 21;
  const second = report();
  second.rows = { content: [], number: 1, size: 20, total_pages: 1, total_elements: 20 };
  jest.mocked(advertisingApi.media).mockResolvedValueOnce(first).mockResolvedValueOnce(second);
  render(<MediaSummaryPage store="1" month="2026-09" canExport={false} />);
  await screen.findByRole('cell', { name: '媒体' });
  fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  expect(await screen.findByText('このページに該当する媒体はありません')).toBeVisible();
  expect(screen.queryByText('この月の広告費は登録されていません')).not.toBeInTheDocument();
});
