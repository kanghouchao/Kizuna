import { serverClient } from '../server-client';

// Mock cookies
const mockCookieStore = {
  get: jest.fn(),
};
jest.mock('next/headers', () => ({
  cookies: jest.fn(() => Promise.resolve(mockCookieStore)),
}));

// Mock fetch
global.fetch = jest.fn();

describe('serverClient', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.TENANT_VALIDATION_API_URL = 'http://test-backend';
  });

  describe('resolveUrl', () => {
    it('バックエンドURLをパスの先頭に付与すること', () => {
      expect(serverClient.resolveUrl('/test')).toBe('http://test-backend/test');
    });

    it('先頭スラッシュがないパスを処理できること', () => {
      expect(serverClient.resolveUrl('test')).toBe('http://test-backend/test');
    });

    it('環境変数が未設定の場合にlocalhostへフォールバックすること', () => {
      delete process.env.TENANT_VALIDATION_API_URL;
      expect(serverClient.resolveUrl('/test')).toBe('http://backend:8080/test');
    });
  });

  describe('getHeaders', () => {
    it('デフォルトヘッダーを返すこと', async () => {
      mockCookieStore.get.mockReturnValue(undefined);
      const headers = await serverClient.getHeaders();
      expect(headers).toEqual({
        'Content-Type': 'application/json',
        'X-Role': 'tenant',
      });
    });

    it('CookieからX-Tenant-IDを注入すること', async () => {
      mockCookieStore.get.mockReturnValue({ value: '123' });
      const headers = await serverClient.getHeaders();
      expect(headers).toHaveProperty('X-Tenant-ID', '123');
    });

    it('カスタムヘッダーをマージすること', async () => {
      mockCookieStore.get.mockReturnValue(undefined);
      const headers = await serverClient.getHeaders({ 'Custom-Header': 'value' });
      expect(headers).toHaveProperty('Custom-Header', 'value');
    });
  });

  describe('get', () => {
    it('正しいURLとヘッダーでfetchを呼び出すこと', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        json: async () => ({ data: 'ok' }),
      });
      mockCookieStore.get.mockReturnValue({ value: '123' });

      const result = await serverClient.get('/test');

      expect(global.fetch).toHaveBeenCalledWith(
        'http://test-backend/test',
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-Tenant-ID': '123',
          }),
        })
      );
      expect(result).toEqual({ data: 'ok' });
    });

    it('エラーレスポンス時に例外をスローすること', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Server Error',
      });

      await expect(serverClient.get('/test')).rejects.toThrow('APIエラー: 500 Server Error');
    });
  });
});
