import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { platformCastApi } from '@/entities/cast';
import { PlatformCastsPage } from '../ui/PlatformCastsPage';

jest.mock('@/entities/cast', () => ({ platformCastApi: { list: jest.fn() } }));
const api = jest.mocked(platformCastApi);

it('本人を検索して本人詳細へのリンクを表示する', async () => {
  api.list.mockResolvedValue({
    rows: [{ id: 3, display_name: '花子', real_name: null }],
    page: 0,
    pageCount: 1,
    total: 1,
  });
  render(<PlatformCastsPage />);
  expect(await screen.findByText('花子')).toBeInTheDocument();
  expect(screen.getByText('未登録')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '在籍を確認' })).toHaveAttribute(
    'href',
    '/platform/casts/3'
  );
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '花子' } });
  fireEvent.click(screen.getByRole('button', { name: '検索' }));
  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith({ page: 0, size: 20, search: '花子' })
  );
});

it('一覧取得の失敗を空結果にせず再試行できる', async () => {
  api.list
    .mockRejectedValueOnce(new Error('failed'))
    .mockResolvedValueOnce({ rows: [], page: 0, pageCount: 0, total: 0 });
  render(<PlatformCastsPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('キャスト一覧を取得できません');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('該当するキャストがいません')).toBeInTheDocument();
});

it('検索条件を保って次ページを取得しクリアで先頭へ戻る', async () => {
  api.list.mockResolvedValue({
    rows: [{ id: 3, display_name: '花子', real_name: '山田花子' }],
    page: 0,
    pageCount: 2,
    total: 21,
  });
  render(<PlatformCastsPage />);
  await screen.findByText('花子');
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '花' } });
  fireEvent.click(screen.getByRole('button', { name: '検索' }));
  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith({ page: 0, size: 20, search: '花' })
  );
  fireEvent.click(screen.getByRole('button', { name: '2' }));
  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith({ page: 1, size: 20, search: '花' })
  );
  fireEvent.click(screen.getByRole('button', { name: 'クリア' }));
  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith({ page: 0, size: 20, search: undefined })
  );
});
