import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { CastEnrollmentPanel } from '../ui/CastEnrollmentPanel';
import { castApi } from '@/entities/cast';

jest.mock('@/entities/cast', () => ({
  ...jest.requireActual('@/entities/cast'),
  castApi: {
    suspend: jest.fn(),
    resume: jest.fn(),
    withdraw: jest.fn(),
    statusHistories: jest.fn(),
    snapshots: jest.fn(),
  },
}));
const api = jest.mocked(castApi);

beforeEach(() => {
  jest.clearAllMocks();
  api.statusHistories.mockResolvedValue({ rows: [], nextCursor: null });
  api.snapshots.mockResolvedValue({ rows: [], nextCursor: null });
});

it('退店は確認後だけ実行し、完了後は状態操作を表示しない', async () => {
  api.withdraw.mockResolvedValue({
    id: 'e1',
    status: 'WITHDRAWN',
    ended_at: '2026-09-10T12:00:00+09:00',
  });
  render(<CastEnrollmentPanel cast={{ id: 'e1', status: 'ENROLLED' }} onChanged={jest.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: '退店する' }));
  let dialog = screen.getByRole('alertdialog');
  expect(within(dialog).getByText(/元に戻せません/)).toBeInTheDocument();
  fireEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }));
  expect(api.withdraw).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '退店する' }));
  dialog = screen.getByRole('alertdialog');
  fireEvent.click(within(dialog).getByRole('button', { name: '退店する' }));
  await waitFor(() =>
    expect(screen.queryByRole('button', { name: '退店する' })).not.toBeInTheDocument()
  );
  expect(screen.getByText('在籍状態: 退店')).toBeInTheDocument();
});

it('履歴を追加読み込みし、失敗時はその領域から再試行できる', async () => {
  api.statusHistories
    .mockResolvedValueOnce({
      rows: [
        {
          id: 'h2',
          previous_status: 'ENROLLED',
          new_status: 'SUSPENDED',
          actor_id: 7,
          recorded_at: '2026-09-10T12:00:00Z',
        },
      ],
      nextCursor: 'next',
    })
    .mockResolvedValueOnce({
      rows: [
        { id: 'h1', new_status: 'ENROLLED', actor_id: 7, recorded_at: '2026-09-09T12:00:00Z' },
      ],
      nextCursor: null,
    });
  api.snapshots.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
    rows: [
      {
        id: 's1',
        actor_id: 7,
        recorded_at: '2026-09-09T12:00:00Z',
        custom_fields: { deleted_key: '変更前' },
      },
    ],
    nextCursor: null,
  });
  render(<CastEnrollmentPanel cast={{ id: 'e1', status: 'SUSPENDED' }} onChanged={jest.fn()} />);
  fireEvent.click(screen.getByText('在籍履歴', { selector: 'summary' }));
  fireEvent.click(screen.getByText('内部情報の編集履歴', { selector: 'summary' }));
  fireEvent.click(await screen.findByRole('button', { name: '在籍履歴をさらに読み込む' }));
  expect(await screen.findByText('入店（在籍中）')).toBeInTheDocument();
  expect(api.statusHistories).toHaveBeenLastCalledWith('e1', 'next');
  fireEvent.click(
    within(screen.getByRole('region', { name: '内部情報の編集履歴' })).getByRole('button', {
      name: '再試行',
    })
  );
  expect(await screen.findByText('deleted_key')).toBeInTheDocument();
  expect(screen.getByText('変更前')).toBeInTheDocument();
});

it('履歴は初期状態で折り畳み、開くと読める', async () => {
  render(<CastEnrollmentPanel cast={{ id: 'e1', status: 'ENROLLED' }} onChanged={jest.fn()} />);
  const summary = screen.getByText('在籍履歴', { selector: 'summary' });
  expect(summary.closest('details')).not.toHaveAttribute('open');
  fireEvent.click(summary);
  expect(await screen.findByText('在籍履歴はありません')).toBeVisible();
});
