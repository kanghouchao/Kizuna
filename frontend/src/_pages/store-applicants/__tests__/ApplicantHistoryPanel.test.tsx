import { fireEvent, render, screen } from '@testing-library/react';
import { applicantApi, type ApplicantHistory } from '@/entities/applicant';
import { ApplicantHistoryPanel } from '../ui/ApplicantHistoryPanel';

jest.mock('@/entities/applicant', () => ({
  ...jest.requireActual('@/entities/applicant'),
  applicantApi: { history: jest.fn() },
}));

const row = (id: string, reason: string): ApplicantHistory => ({
  id,
  new_status: 'RECEIVED',
  reason,
  actor_id: 1,
  created_at: '2026-10-01T00:00:00Z',
});

it('選考履歴をページ番号で送り、次のページを取り直す', async () => {
  jest
    .mocked(applicantApi.history)
    .mockResolvedValueOnce({ rows: [row('h1', '一件目')], page: 0, pageCount: 2, total: 2 })
    .mockResolvedValueOnce({ rows: [row('h2', '二件目')], page: 1, pageCount: 2, total: 2 });
  render(<ApplicantHistoryPanel id="a" />);
  expect(await screen.findByText('一件目')).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  expect(await screen.findByText('二件目')).toBeInTheDocument();
  expect(screen.queryByText('一件目')).not.toBeInTheDocument();
  expect(applicantApi.history).toHaveBeenLastCalledWith('a', 1);
});
