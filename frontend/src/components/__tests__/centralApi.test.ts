import { centralTenantApi } from '@/entities/tenant';
import { centralAuthApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { url } })),
    post: jest.fn(async (_url: string, _data?: any) => ({ data: {} })),
    put: jest.fn(async (_url: string, _data?: any) => ({ data: {} })),
    delete: jest.fn(async (_url: string) => ({ data: {} })),
  },
}));

describe('central api', () => {
  it('getList delegates to /central/tenants', async () => {
    const res = await centralTenantApi.getList({ page: 1 });
    expect(res).toHaveProperty('url', '/central/tenants');
  });

  it('login returns data', async () => {
    const res = await centralAuthApi.login({ username: 'a', password: 'b' } as any);
    expect(res).toEqual({});
  });
});
