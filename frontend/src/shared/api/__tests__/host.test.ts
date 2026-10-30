import { resolveHost } from '../host';

const requestHeaders = new Map<string, string>();
jest.mock('next/headers', () => ({
  headers: jest.fn(() =>
    Promise.resolve({ get: (name: string) => requestHeaders.get(name) ?? null })
  ),
}));

global.fetch = jest.fn();

function respond(status: number, body?: unknown) {
  (global.fetch as jest.Mock).mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  });
}

describe('resolveHost', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    requestHeaders.clear();
    process.env.APP_DOMAIN = 'app.example';
    process.env.BACKEND_URL = 'http://test-backend';
  });

  it('アプリドメインはバックエンドを引かずに app と判定する', async () => {
    requestHeaders.set('host', 'app.example:3000');

    await expect(resolveHost()).resolves.toEqual({ kind: 'app' });
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('店舗ドメインは毎回キャッシュせずに lookup し、店舗の id と名前を返す', async () => {
    requestHeaders.set('host', 'a.example');
    respond(200, { id: 7, name: '店舗A' });

    await expect(resolveHost()).resolves.toEqual({
      kind: 'store',
      store: { id: '7', name: '店舗A' },
    });
    expect(global.fetch).toHaveBeenCalledWith(
      'http://test-backend/stores/lookup?domain=a.example',
      { cache: 'no-store' }
    );
  });

  it('x-forwarded-host の先頭を host より優先し、小文字にしてポートを外す', async () => {
    requestHeaders.set('host', 'internal:3000');
    requestHeaders.set('x-forwarded-host', 'A.Example:443, proxy.example');
    respond(200, { id: 7, name: '店舗A' });

    await resolveHost();

    expect(global.fetch).toHaveBeenCalledWith(
      'http://test-backend/stores/lookup?domain=a.example',
      expect.anything()
    );
  });

  it('lookup の 404 だけを未登録とする', async () => {
    requestHeaders.set('host', 'gone.example');
    respond(404);

    await expect(resolveHost()).resolves.toEqual({ kind: 'unknown' });
  });

  it('lookup の 5xx は未登録に倒さず例外にする', async () => {
    requestHeaders.set('host', 'a.example');
    respond(503);

    await expect(resolveHost()).rejects.toThrow('503');
  });
});
