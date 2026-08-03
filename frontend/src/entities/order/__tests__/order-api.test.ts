import { memberOrderApi, orderApi } from '@/entities/order';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

const mockedGet = apiClient.get as jest.Mock;

describe('orderApi', () => {
  it('list は /store/orders を GET し、Spring Page を PageResult へ正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 'o1' }],
        total_pages: 3,
        total_elements: 25,
        size: 10,
        number: 1,
      },
    });

    await expect(orderApi.list({ page: 1, size: 10 })).resolves.toEqual({
      rows: [{ id: 'o1' }],
      page: 1,
      pageCount: 3,
      total: 25,
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/orders', { params: { page: 1, size: 10 } });
  });
  it('create は /store/orders を POST する', async () => {
    expect(await orderApi.create({} as never)).toEqual({ ok: true, url: '/store/orders' });
  });
  it('listReceptionists は /store/orders/receptionists を GET する', async () => {
    expect(await orderApi.listReceptionists()).toEqual({
      ok: true,
      url: '/store/orders/receptionists',
    });
  });
  it('listReservationRequests は予約受付の専用読み口を GET し、Spring Page を正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 'o1' }],
        total_pages: 2,
        total_elements: 21,
        size: 20,
        number: 0,
      },
    });

    // 絞り込みは専用読み口の責務。受注一覧を取って手元で選り分ける形へは戻さない。
    await expect(orderApi.listReservationRequests({ page: 0, size: 20 })).resolves.toEqual({
      rows: [{ id: 'o1' }],
      page: 0,
      pageCount: 2,
      total: 21,
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/orders/reservation-requests', {
      params: { page: 0, size: 20 },
    });
  });
  it('confirm は確定の子リソースを POST する', async () => {
    expect(await orderApi.confirm('o1')).toEqual({
      ok: true,
      url: '/store/orders/o1/confirmation',
    });
  });
  it('decline は謝絶の子リソースを POST する', async () => {
    expect(await orderApi.decline('o1')).toEqual({ ok: true, url: '/store/orders/o1/decline' });
  });
});

describe('memberOrderApi', () => {
  it('list は /platform/me/orders を GET し、Spring Page を PageResult へ正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 'o1' }],
        total_pages: 1,
        total_elements: 1,
        size: 20,
        number: 0,
      },
    });

    await expect(memberOrderApi.list()).resolves.toEqual({
      rows: [{ id: 'o1' }],
      page: 0,
      pageCount: 1,
      total: 1,
    });
    expect(mockedGet).toHaveBeenCalledWith('/platform/me/orders', { params: undefined });
  });
  it('create は /platform/me/orders を POST する', async () => {
    expect(await memberOrderApi.create({} as never)).toEqual({
      ok: true,
      url: '/platform/me/orders',
    });
  });
  it('cancel は取り下げの子リソースを POST する', async () => {
    expect(await memberOrderApi.cancel('o1')).toEqual({
      ok: true,
      url: '/platform/me/orders/o1/cancellation',
    });
  });
});
