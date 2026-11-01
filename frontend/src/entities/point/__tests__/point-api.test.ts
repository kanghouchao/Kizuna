import { memberPointApi } from '@/entities/point';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

const mockedGet = apiClient.get as jest.Mock;

describe('memberPointApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('balance は本人向けの残高の読み口を GET する', async () => {
    // 店舗側の照会（/customers/{id}/member-point-balance）は顧客管理権限が要る別の口。
    expect(await memberPointApi.balance()).toEqual({
      ok: true,
      url: '/me/points/balance',
    });
  });

  it('entries は明細をページ番号で GET し、ページを正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ amount: 100 }], number: 1, total_pages: 3, total_elements: 41 },
    });

    await expect(memberPointApi.entries(1)).resolves.toEqual({
      rows: [{ amount: 100 }],
      page: 1,
      pageCount: 3,
      total: 41,
    });
    expect(mockedGet).toHaveBeenCalledWith('/me/points/entries', {
      params: { page: 1, size: 20 },
    });
  });
});
