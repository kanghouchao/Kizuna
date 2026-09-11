import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { platformCastApi } from '@/entities/cast';
import { PlatformCastDetailPage } from '../ui/PlatformCastDetailPage';

jest.mock('next/navigation', () => ({ useParams: () => ({ id: '3' }) }));
jest.mock('@/entities/cast', () => ({
  platformCastApi: { get: jest.fn(), enrollments: jest.fn() },
}));
const api = jest.mocked(platformCastApi);
beforeEach(() => jest.clearAllMocks());

it('本人属性と店舗ごとの在籍を読み取り専用で表示する', async () => {
  api.get.mockResolvedValue({
    id: 3,
    platform_user_id: 7,
    display_name: '花子',
    real_name: null,
    birth_date: null,
  });
  api.enrollments.mockResolvedValue({
    rows: [
      {
        id: '8',
        store_id: 1,
        store_name: '東京店',
        name: 'さくら',
        status: 'ENROLLED',
        ended_at: null,
      },
      {
        id: '9',
        store_id: 2,
        store_name: '大阪店',
        name: 'はな',
        status: 'WITHDRAWN',
        ended_at: '2026-09-01T12:00:00+09:00',
      },
    ],
    page: 0,
    pageCount: 1,
    total: 2,
  });
  render(<PlatformCastDetailPage />);
  expect(await screen.findByText('花子')).toBeInTheDocument();
  expect(await screen.findByText('大阪店')).toBeInTheDocument();
  expect(screen.getByText('退店')).toBeInTheDocument();
  expect(screen.getAllByText('未登録')).toHaveLength(2);
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '編集' })).not.toBeInTheDocument();
});

it('本人が存在しないとき一覧へ戻れる', async () => {
  api.get.mockRejectedValueOnce({ response: { status: 404 } });
  render(<PlatformCastDetailPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('キャスト本人が見つかりません');
  expect(screen.getByRole('link', { name: '一覧に戻る' })).toHaveAttribute(
    'href',
    '/platform/casts'
  );
  expect(api.enrollments).not.toHaveBeenCalled();
});

it('本人取得を再試行して空の在籍を表示する', async () => {
  api.get.mockRejectedValueOnce({ response: { status: 403 } }).mockResolvedValueOnce({
    id: 3,
    platform_user_id: 7,
    display_name: '花子',
    real_name: '山田花子',
    birth_date: '1995-04-03',
  });
  api.enrollments.mockResolvedValueOnce({ rows: [], page: 0, pageCount: 0, total: 0 });
  render(<PlatformCastDetailPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('閲覧権限');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('店舗在籍がありません')).toBeInTheDocument();
  expect(screen.getByText('1995-04-03')).toBeInTheDocument();
});

it('在籍領域だけを再試行しページ送りできる', async () => {
  api.get.mockResolvedValue({
    id: 3,
    platform_user_id: 7,
    display_name: '花子',
    real_name: null,
    birth_date: null,
  });
  api.enrollments.mockRejectedValueOnce(new Error('failed')).mockResolvedValue({
    rows: [
      {
        id: '8',
        store_id: 1,
        store_name: '東京店',
        name: 'さくら',
        status: 'SUSPENDED',
        ended_at: null,
      },
    ],
    page: 0,
    pageCount: 2,
    total: 21,
  });
  render(<PlatformCastDetailPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('店舗在籍を取得できません');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('在籍停止')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '2' }));
  await waitFor(() => expect(api.enrollments).toHaveBeenLastCalledWith(3, { page: 1, size: 20 }));
  expect(api.get).toHaveBeenCalledTimes(1);
});
