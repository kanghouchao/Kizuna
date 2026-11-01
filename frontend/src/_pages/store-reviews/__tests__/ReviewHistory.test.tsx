import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { reviewApi, type ReviewHistory as Row } from '@/entities/review';
import { ReviewHistory } from '../ui/ReviewHistory';

jest.mock('@/entities/review', () => ({
  ...jest.requireActual('@/entities/review'),
  reviewApi: { history: jest.fn() },
}));

function row(id: string, reason: string) {
  return {
    id,
    type: 'APPROVED',
    created_at: '2026-09-01T00:00:00Z',
    actor: { display_name: '担当者' },
    after_version: 1,
    reason,
    related_review_id: null,
  } as unknown as Row;
}

it('操作履歴をページ番号で送り、次のページを取り直す', async () => {
  jest
    .mocked(reviewApi.history)
    .mockResolvedValueOnce({ rows: [row('a', '一件目')], page: 0, pageCount: 2, total: 2 })
    .mockResolvedValueOnce({ rows: [row('b', '二件目')], page: 1, pageCount: 2, total: 2 });
  render(<ReviewHistory id="r1" />);
  expect(await screen.findByText('一件目')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '次のページ' }));
  expect(await screen.findByText('二件目')).toBeInTheDocument();
  expect(screen.queryByText('一件目')).not.toBeInTheDocument();
  await waitFor(() => expect(reviewApi.history).toHaveBeenLastCalledWith('r1', 1));
});
