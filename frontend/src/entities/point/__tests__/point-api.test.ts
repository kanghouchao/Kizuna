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
    // 店舗側の照会（/store/customers/{id}/member-point-balance）は顧客管理権限が要る別の口。
    expect(await memberPointApi.balance()).toEqual({
      ok: true,
      url: '/platform/me/points/balance',
    });
  });

  it('entries は明細を GET し、カーソルページを正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ amount: 100 }], next_cursor: 'c1' },
    });

    await expect(memberPointApi.entries({ cursor: 'c0', size: 20 })).resolves.toEqual({
      rows: [{ amount: 100 }],
      nextCursor: 'c1',
    });
    expect(mockedGet).toHaveBeenCalledWith('/platform/me/points/entries', {
      params: { cursor: 'c0', size: 20 },
    });
  });

  it('続きが無い応答では位置を null に畳む', async () => {
    mockedGet.mockResolvedValueOnce({ data: { content: [] } });

    await expect(memberPointApi.entries()).resolves.toEqual({ rows: [], nextCursor: null });
  });
});
