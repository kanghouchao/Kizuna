import { systemConfigService } from '@/entities/system-config';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

describe('systemConfigService', () => {
  it('getAllConfigs は /platform/configs を GET する', async () => {
    expect(await systemConfigService.getAllConfigs()).toEqual({
      ok: true,
      url: '/platform/configs',
    });
  });
  it('updateConfig は /platform/configs を PUT する', async () => {
    expect(await systemConfigService.updateConfig({ config_key: 'k', config_value: 'v' })).toEqual({
      ok: true,
      url: '/platform/configs',
    });
  });
});
