import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { advertisingApi, type Change } from '../api/advertising';
import { CostHistory } from './CostHistory';
jest.mock('../api/advertising', () => ({
  ...jest.requireActual('../api/advertising'),
  advertisingApi: { changes: jest.fn(), change: jest.fn() },
}));
function change(id: string): Change {
  return {
    id,
    cost_id: `cost-${id}`,
    action: 'CREATED',
    actor_id: 1,
    occurred_at: '2026-09-01T00:00:00Z',
    version_before: null,
    version_after: 1,
  };
}
function page(rows: Change[], number: number, totalPages: number) {
  return {
    content: rows,
    number,
    size: 20,
    total_pages: totalPages,
    total_elements: rows.length,
  };
}
beforeEach(() => jest.clearAllMocks());
it('変更履歴をページ番号で送り、次のページを取り直す', async () => {
  jest
    .mocked(advertisingApi.changes)
    .mockResolvedValueOnce(page([change('a')], 0, 2))
    .mockResolvedValueOnce(page([change('b')], 1, 2));
  render(<CostHistory month="2026-09" />);
  expect(await screen.findByText('cost-a')).toBeInTheDocument();
  expect(screen.getByText('1 / 2')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '次のページ' }));
  expect(await screen.findByText('cost-b')).toBeInTheDocument();
  expect(screen.queryByText('cost-a')).not.toBeInTheDocument();
  await waitFor(() => expect(advertisingApi.changes).toHaveBeenLastCalledWith('2026-09', 1));
});
it('1 ページに収まる履歴ではページ送りを出さない', async () => {
  jest.mocked(advertisingApi.changes).mockResolvedValue(page([change('a')], 0, 1));
  render(<CostHistory month="2026-09" />);
  expect(await screen.findByText('cost-a')).toBeInTheDocument();
  expect(screen.queryByRole('navigation', { name: 'ページ送り' })).not.toBeInTheDocument();
});
