import { customerApi } from '@/entities/customer';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async () => ({ data: undefined })),
  },
}));

const mockedGet = apiClient.get as jest.Mock;
const mockedPost = apiClient.post as jest.Mock;

describe('customerApi', () => {
  it('list は /store/customers を GET し、Spring Page を PageResult へ正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 'c1' }],
        total_pages: 2,
        total_elements: 21,
        size: 20,
        number: 0,
      },
    });

    await expect(customerApi.list({ page: 0, size: 20, search: '山田' })).resolves.toEqual({
      rows: [{ id: 'c1' }],
      page: 0,
      pageCount: 2,
      total: 21,
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/customers', {
      params: { page: 0, size: 20, search: '山田' },
    });
  });
  it('get は /store/customers/:id を GET する', async () => {
    expect(await customerApi.get('c1')).toEqual({ ok: true, url: '/store/customers/c1' });
  });
  it('duplicates は /store/customers/duplicates を GET する', async () => {
    // 字面セグメント。/{id} と同じパス空間に住むので、綴りが変わると詳細取得へ吸われる
    expect(await customerApi.duplicates()).toEqual({
      ok: true,
      url: '/store/customers/duplicates',
    });
  });
  it('merge は存続行の配下へ被統合行の ID を snake_case で POST する', async () => {
    expect(await customerApi.merge('c1', 'c2')).toEqual({
      ok: true,
      url: '/store/customers/c1/merges',
    });
    expect(mockedPost).toHaveBeenLastCalledWith('/store/customers/c1/merges', {
      merged_customer_id: 'c2',
    });
  });
  it('create は /store/customers を POST する', async () => {
    expect(await customerApi.create({ name: 'A' })).toEqual({
      ok: true,
      url: '/store/customers',
    });
  });
  it('update は /store/customers/:id を PUT する', async () => {
    expect(await customerApi.update('c1', {})).toEqual({ ok: true, url: '/store/customers/c1' });
  });
  it('delete は /store/customers/:id を DELETE する', async () => {
    await expect(customerApi.delete('c1')).resolves.toBeUndefined();
  });
  it('linkMember は member-link を snake_case の本文で POST する', async () => {
    expect(await customerApi.linkMember('c1', '123456789012')).toEqual({
      ok: true,
      url: '/store/customers/c1/member-link',
    });
    expect(mockedPost).toHaveBeenLastCalledWith('/store/customers/c1/member-link', {
      member_code: '123456789012',
    });
  });
  it('unlinkMember は member-link を DELETE する', async () => {
    await expect(customerApi.unlinkMember('c1')).resolves.toBeUndefined();
  });
  it('memberLink は現に有効な紐づけを GET する', async () => {
    mockedGet.mockResolvedValueOnce({ data: { linked: true, member_code: '123456789012' } });

    await expect(customerApi.memberLink('c1')).resolves.toEqual({
      linked: true,
      member_code: '123456789012',
    });
    expect(mockedGet).toHaveBeenLastCalledWith('/store/customers/c1/member-link');
  });
  it('memberLinkHistory は member-link/history を GET し、カーソルページを正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ id: 'l1', status: 'ACTIVE' }], next_cursor: 'abc' },
    });

    await expect(customerApi.memberLinkHistory('c1', { cursor: 'x' })).resolves.toEqual({
      rows: [{ id: 'l1', status: 'ACTIVE' }],
      nextCursor: 'abc',
    });
    expect(mockedGet).toHaveBeenLastCalledWith('/store/customers/c1/member-link/history', {
      params: { cursor: 'x' },
    });
  });
  it('memberPointBalance は member-point-balance を GET する', async () => {
    mockedGet.mockResolvedValueOnce({ data: { linked: true, balance: 120 } });

    await expect(customerApi.memberPointBalance('c1')).resolves.toEqual({
      linked: true,
      balance: 120,
    });
    expect(mockedGet).toHaveBeenLastCalledWith('/store/customers/c1/member-point-balance');
  });
  it('adjustPoints は point-adjustments を snake_case の本文で POST する', async () => {
    mockedPost.mockResolvedValueOnce({ data: { linked: true, balance: 220 } });

    await expect(
      customerApi.adjustPoints('c1', {
        delta: 100,
        reason: '手動付与',
        expires_on: '2026-12-31',
        idempotency_key: 'idem-1',
      })
    ).resolves.toEqual({ linked: true, balance: 220 });
    expect(mockedPost).toHaveBeenLastCalledWith('/store/customers/c1/point-adjustments', {
      delta: 100,
      reason: '手動付与',
      expires_on: '2026-12-31',
      idempotency_key: 'idem-1',
    });
  });
});
