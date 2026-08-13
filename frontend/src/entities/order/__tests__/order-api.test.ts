import { memberOrderApi, memberReceiptApi, memberVisitApi, orderApi } from '@/entities/order';
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
  it('listCastCandidates は受注側の指名候補の読み口を GET する', async () => {
    // キャスト管理一覧（/store/casts）を流用すると受注権限だけでは 403 になり、在籍停止も混ざる
    expect(await orderApi.listCastCandidates({ search: '花' })).toEqual({
      ok: true,
      url: '/store/orders/cast-candidates',
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/orders/cast-candidates', {
      params: { search: '花' },
    });
  });
  it('listReservationRequests は予約受付の専用読み口を GET し、カーソルページを正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ id: 'o1' }], next_cursor: 'abc' },
    });

    // 絞り込みは専用読み口の責務。受注一覧を取って手元で選り分ける形へは戻さない。
    await expect(orderApi.listReservationRequests({ cursor: 'abc', size: 20 })).resolves.toEqual({
      rows: [{ id: 'o1' }],
      nextCursor: 'abc',
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/orders/reservation-requests', {
      params: { cursor: 'abc', size: 20 },
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
  it('attribution は受注の帰属の現況を GET する', async () => {
    expect(await orderApi.attribution('o1')).toEqual({
      ok: true,
      url: '/store/orders/o1/attribution',
    });
  });
  it('invalidateAttribution は理由を添えて無効化の子リソースを POST する', async () => {
    // 理由はこの訂正の唯一の根拠。省略できる形にすると、後から誰の来店が消えたのか辿れなくなる
    expect(
      await orderApi.invalidateAttribution('o1', { attribution_id: 501, reason: '取り違え' })
    ).toEqual({
      ok: true,
      url: '/store/orders/o1/attribution/invalidation',
    });
    expect(apiClient.post).toHaveBeenCalledWith('/store/orders/o1/attribution/invalidation', {
      attribution_id: 501,
      reason: '取り違え',
    });
  });
  it('reissueReceiptToken は伝票トークンの子リソースを POST する', async () => {
    expect(await orderApi.reissueReceiptToken('o1')).toEqual({
      ok: true,
      url: '/store/orders/o1/receipt-token',
    });
  });
});

describe('memberOrderApi', () => {
  it('list は /platform/me/orders を GET し、カーソルページを正規化する', async () => {
    // 続きが無いときサーバは next_cursor を省く（null 非出力方針）
    mockedGet.mockResolvedValueOnce({ data: { content: [{ id: 'o1' }] } });

    await expect(memberOrderApi.list()).resolves.toEqual({
      rows: [{ id: 'o1' }],
      nextCursor: null,
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

describe('memberVisitApi', () => {
  it('list は来店履歴を GET し、カーソルページを正規化する', async () => {
    // 申請の追跡（/platform/me/orders）とは別の読み口で、確定した来店だけを返す
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ granted_points: 120 }], next_cursor: 'c1' },
    });

    await expect(memberVisitApi.list({ cursor: 'c0', size: 20 })).resolves.toEqual({
      rows: [{ granted_points: 120 }],
      nextCursor: 'c1',
    });
    expect(mockedGet).toHaveBeenCalledWith('/platform/me/visits', {
      params: { cursor: 'c0', size: 20 },
    });
  });

  it('続きが無い応答では位置を null に畳む', async () => {
    mockedGet.mockResolvedValueOnce({ data: { content: [] } });

    await expect(memberVisitApi.list()).resolves.toEqual({ rows: [], nextCursor: null });
  });
});

describe('memberReceiptApi', () => {
  it('claim は /platform/me/receipts/claim を POST し、トークンを本体で送る', async () => {
    // パスや問い合わせ文字列に載せると、90 日有効の生値がアクセスログに残る
    const mockedPost = apiClient.post as jest.Mock;
    mockedPost.mockResolvedValueOnce({ data: { granted_points: 120 } });

    await expect(memberReceiptApi.claim('tok3n')).resolves.toEqual({ granted_points: 120 });
    expect(mockedPost).toHaveBeenCalledWith('/platform/me/receipts/claim', { token: 'tok3n' });
  });
});
