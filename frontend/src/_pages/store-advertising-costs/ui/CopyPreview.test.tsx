import { render, screen, waitFor, act } from '@testing-library/react';
import { advertisingApi, type Month } from '../api/advertising';
import { CopyPreview } from './CopyPreview';
jest.mock('../api/advertising', () => ({
  ...jest.requireActual('../api/advertising'),
  advertisingApi: { month: jest.fn(), list: jest.fn() },
}));
it('明細取得後に元月の版を確認し、途中更新ならコピーを止める', async () => {
  const month = jest.mocked(advertisingApi.month);
  const list = jest.mocked(advertisingApi.list);
  let resolve!: (value: never) => void;
  month
    .mockResolvedValueOnce({ version: 1 } as Month)
    .mockResolvedValueOnce({ version: 0 } as Month)
    .mockResolvedValueOnce({ version: 2 } as Month);
  list.mockImplementationOnce(
    () =>
      new Promise(r => {
        resolve = r;
      })
  );
  render(<CopyPreview month="2026-10" disabled={false} onSubmit={jest.fn()} />);
  await waitFor(() => expect(list).toHaveBeenCalledTimes(1));
  expect(month.mock.calls).toEqual([['2026-09'], ['2026-10']]);
  await act(async () => resolve({ content: [], total_pages: 1 } as never));
  expect(
    await screen.findByText('前月が更新されたか、取得に失敗しました。先頭から再確認してください。')
  ).toBeVisible();
  expect(screen.queryByRole('button', { name: '金額を確認してコピー' })).not.toBeInTheDocument();
});
