import { platformCastApi } from '@/entities/cast';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({ __esModule: true, default: { get: jest.fn() } }));

it('本人検索の Spring Page を正規化する', async () => {
  jest.mocked(apiClient.get).mockResolvedValueOnce({
    data: {
      content: [{ id: 1, display_name: '花', real_name: null }],
      total_pages: 2,
      total_elements: 21,
      number: 1,
    },
  });
  expect(await platformCastApi.list({ page: 1, size: 20, search: '花' })).toEqual({
    rows: [{ id: 1, display_name: '花', real_name: null }],
    page: 1,
    pageCount: 2,
    total: 21,
  });
  expect(apiClient.get).toHaveBeenLastCalledWith('/platform/casts', {
    params: { page: 1, size: 20, search: '花' },
  });
});

it('本人詳細を取得する', async () => {
  const person = {
    id: 1,
    display_name: '花',
    real_name: null,
    birth_date: null,
    platform_user_id: 4,
  };
  jest.mocked(apiClient.get).mockResolvedValueOnce({ data: person });
  expect(await platformCastApi.get(1)).toEqual(person);
  expect(apiClient.get).toHaveBeenLastCalledWith('/platform/casts/1');
});

it('在籍の次ページを取得する', async () => {
  jest
    .mocked(apiClient.get)
    .mockResolvedValueOnce({ data: { content: [], total_pages: 0, total_elements: 0, number: 0 } });
  expect(await platformCastApi.enrollments(1, { page: 0, size: 20 })).toEqual({
    rows: [],
    page: 0,
    pageCount: 0,
    total: 0,
  });
  expect(apiClient.get).toHaveBeenLastCalledWith('/platform/casts/1/enrollments', {
    params: { page: 0, size: 20 },
  });
});
