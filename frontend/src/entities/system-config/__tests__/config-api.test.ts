import { systemConfigService } from '@/entities/system-config';
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

describe('systemConfigService', () => {
  it('getAllConfigs は /platform/configs を GET する', async () => {
    expect(await systemConfigService.getAllConfigs()).toEqual({
      ok: true,
      url: '/platform/configs',
    });
  });
  it('updateConfig は /platform/configs/{key} を PUT する', async () => {
    expect(await systemConfigService.updateConfig('smtp_port', { config_value: 'v' })).toEqual({
      ok: true,
      url: '/platform/configs/smtp_port',
    });
  });
});

/**
 * 識別子を欠いた呼び出しは要求そのものを組まない。応答 DTO の項目はすべて可選なので、画面側が
 * `?? ''` で素通しすると単数の操作が一覧の URI へ飛び、届いた先の 404/405 が操作の失敗と
 * 見分けられなくなる。守りはアダプタの内側にあり、画面ごとに書かない。
 */
describe('識別子を欠いた systemConfigService', () => {
  const calls: [string, () => Promise<unknown>, string][] = [
    [
      'updateConfig',
      () => systemConfigService.updateConfig(undefined, { config_value: 'v' }),
      '設定',
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
