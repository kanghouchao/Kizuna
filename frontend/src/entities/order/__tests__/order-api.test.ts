import {
  memberOrderApplicationApi,
  memberReceiptApi,
  memberVisitApi,
  orderApi,
  orderApplicationApi,
} from '@/entities/order';
import { apiClient } from '@/shared/api';
import { ClientDataError } from '@/shared/lib';

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
const mockedPost = apiClient.post as jest.Mock;

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
  it('get は受注 1 件を GET する', async () => {
    expect(await orderApi.get('o1')).toEqual({ ok: true, url: '/store/orders/o1' });
  });
  it('update は受注を PUT する', async () => {
    expect(await orderApi.update('o1', { pax: 3 })).toEqual({ ok: true, url: '/store/orders/o1' });
  });
  it('cancel は取消の子リソースを POST し、本体を返さない', async () => {
    // 応答は 204。呼出側は行を消すだけで結果を読まない
    await expect(orderApi.cancel('o1', { reason: '客都合' })).resolves.toBeUndefined();
    expect(mockedPost).toHaveBeenLastCalledWith('/store/orders/o1/cancellation', {
      reason: '客都合',
    });
  });
  it('listWorkQueue は群読み口を GET し、カーソルページを正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ id: 'o1' }], next_cursor: 'abc' },
    });

    // 絞り込みは専用読み口の責務。受注一覧を取って手元で選り分ける形へは戻さない。
    await expect(
      orderApi.listWorkQueue({
        statuses: ['CONFIRMED', 'COMPLETED'],
        sort_key: 'PAX',
        desc: true,
        cursor: 'abc',
        size: 20,
      })
    ).resolves.toEqual({ rows: [{ id: 'o1' }], nextCursor: 'abc' });
    expect(mockedGet).toHaveBeenCalledWith('/store/orders/work-queue', {
      params: {
        statuses: 'CONFIRMED,COMPLETED',
        sort_key: 'PAX',
        desc: true,
        cursor: 'abc',
        size: 20,
      },
    });
  });
  it('listWorkQueue は空欄の検索条件を要求から落とす', async () => {
    mockedGet.mockResolvedValueOnce({ data: { content: [], next_cursor: null } });

    await orderApi.listWorkQueue({
      statuses: ['CONFIRMED'],
      customer_name: '',
      business_date: '',
      size: 20,
    });

    // 空欄を送ると「絞り込んでいない」ことが要求から読めなくなる
    expect(mockedGet).toHaveBeenCalledWith('/store/orders/work-queue', {
      params: { statuses: 'CONFIRMED', size: 20 },
    });
  });
  it('listArchive はアーカイブを GET し、Spring Page を PageResult へ正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ id: 'o1' }], total_pages: 2, total_elements: 7, size: 5, number: 1 },
    });

    await expect(
      orderApi.listArchive({ statuses: ['COMPLETED'], page: 1, size: 5 })
    ).resolves.toEqual({ rows: [{ id: 'o1' }], page: 1, pageCount: 2, total: 7 });
    expect(mockedGet).toHaveBeenCalledWith('/store/orders/archive', {
      params: { statuses: 'COMPLETED', page: 1, size: 5 },
    });
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

describe('orderApplicationApi', () => {
  it('list は受付箱を GET し、状態をカンマ区切りで送ってカーソルページを正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ id: 'a1' }], next_cursor: 'abc' },
    });

    await expect(
      orderApplicationApi.list({ statuses: ['PENDING'], cursor: 'c0', size: 20 })
    ).resolves.toEqual({ rows: [{ id: 'a1' }], nextCursor: 'abc' });
    expect(mockedGet).toHaveBeenCalledWith('/store/order-applications', {
      params: { statuses: 'PENDING', cursor: 'c0', size: 20 },
    });
  });
  it('confirm は確定の子リソースへ確定内容を POST し、生成された受注を返す', async () => {
    expect(
      await orderApplicationApi.confirm('a1', { business_date: '2026-08-20', pax: 2 })
    ).toEqual({
      ok: true,
      url: '/store/order-applications/a1/confirmation',
    });
    expect(mockedPost).toHaveBeenLastCalledWith('/store/order-applications/a1/confirmation', {
      business_date: '2026-08-20',
      pax: 2,
    });
  });
  it('decline は謝絶の子リソースへ理由を POST し、本体を返さない', async () => {
    // 理由は謝絶の唯一の根拠。省略できる形にすると、後から経緯を辿れなくなる
    await expect(orderApplicationApi.decline('a1', { reason: '満席' })).resolves.toBeUndefined();
    expect(mockedPost).toHaveBeenLastCalledWith('/store/order-applications/a1/refusal', {
      reason: '満席',
    });
  });
});

describe('memberOrderApplicationApi', () => {
  it('list は /platform/me/order-applications を GET し、カーソルページを正規化する', async () => {
    // 続きが無いときサーバは next_cursor を省く（null 非出力方針）
    mockedGet.mockResolvedValueOnce({ data: { content: [{ id: 'a1' }] } });

    await expect(memberOrderApplicationApi.list()).resolves.toEqual({
      rows: [{ id: 'a1' }],
      nextCursor: null,
    });
    expect(mockedGet).toHaveBeenCalledWith('/platform/me/order-applications', {
      params: undefined,
    });
  });
  it('create は /platform/me/order-applications を POST する', async () => {
    expect(await memberOrderApplicationApi.create({} as never)).toEqual({
      ok: true,
      url: '/platform/me/order-applications',
    });
  });
  it('withdraw は取り下げの子リソースを POST する', async () => {
    expect(await memberOrderApplicationApi.withdraw('a1')).toEqual({
      ok: true,
      url: '/platform/me/order-applications/a1/withdrawal',
    });
  });
});

describe('memberVisitApi', () => {
  it('list は来店履歴を GET し、カーソルページを正規化する', async () => {
    // 申請の追跡（/platform/me/order-applications）とは別の読み口で、確定した来店だけを返す
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
  it('claim は /platform/me/receipts を POST し、トークンを本体で送る', async () => {
    // パスや問い合わせ文字列に載せると、90 日有効の生値がアクセスログに残る
    const mockedPost = apiClient.post as jest.Mock;
    mockedPost.mockResolvedValueOnce({ data: { granted_points: 120 } });

    await expect(memberReceiptApi.claim('tok3n')).resolves.toEqual({ granted_points: 120 });
    expect(mockedPost).toHaveBeenCalledWith('/platform/me/receipts', { token: 'tok3n' });
  });
});

/**
 * 識別子を欠いた呼び出しは要求そのものを組まない。応答 DTO の項目はすべて可選なので、画面側が
 * `?? ''` で素通しすると単数の操作が一覧の URI へ飛び、届いた先の 404/405 が操作の失敗と
 * 見分けられなくなる。守りはアダプタの内側にあり、画面ごとに書かない。
 */
describe('識別子を欠いた orderApi / orderApplicationApi / memberOrderApplicationApi', () => {
  const calls: [string, () => Promise<unknown>, string][] = [
    ['get', () => orderApi.get(undefined), '受注'],
    ['update', () => orderApi.update(undefined, {}), '受注'],
    ['cancel', () => orderApi.cancel(undefined, { reason: 'r' }), '受注'],
    [
      'orderApplicationApi.confirm',
      () => orderApplicationApi.confirm(undefined, { business_date: '2026-08-20' }),
      '予約申請',
    ],
    [
      'orderApplicationApi.decline',
      () => orderApplicationApi.decline(undefined, { reason: 'r' }),
      '予約申請',
    ],
    ['complete', () => orderApi.complete(undefined, { total_fee: 1 }), '受注'],
    ['completionPreview', () => orderApi.completionPreview(undefined, 0), '受注'],
    ['attribution', () => orderApi.attribution(undefined), '受注'],
    [
      'invalidateAttribution',
      () => orderApi.invalidateAttribution(undefined, { attribution_id: 1, reason: 'r' }),
      '受注',
    ],
    ['reissueReceiptToken', () => orderApi.reissueReceiptToken(undefined), '受注'],
    ['attributionCorrection', () => orderApi.attributionCorrection(undefined, 1), '受注'],
    [
      'correctAttributionPoints',
      () =>
        orderApi.correctAttributionPoints(undefined, {
          attribution_id: 1,
          points: 1,
          reason: 'r',
          idempotency_key: 'k',
        }),
      '受注',
    ],
    [
      'memberOrderApplicationApi.withdraw',
      () => memberOrderApplicationApi.withdraw(undefined),
      '予約申請',
    ],
  ];

  it.each(calls)('%s は要求を出さず、名乗る失敗を投げる', async (_name, call, label) => {
    jest.clearAllMocks();

    await expect(call()).rejects.toBeInstanceOf(ClientDataError);
    await expect(call()).rejects.toThrow(`${label}の識別子が取得できていません`);
    expect(apiClient.get).not.toHaveBeenCalled();
    expect(apiClient.post).not.toHaveBeenCalled();
    expect(apiClient.put).not.toHaveBeenCalled();
    expect(apiClient.delete).not.toHaveBeenCalled();
  });
});
