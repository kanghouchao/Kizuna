import { ServerApiError, serverClient } from '../server-client';

// fetchのモック
global.fetch = jest.fn();

describe('serverClient', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.BACKEND_URL = 'http://test-backend';
  });

  describe('resolveUrl', () => {
    it('BACKEND_URL をパスの先頭に付与すること', () => {
      expect(serverClient.resolveUrl('/test')).toBe('http://test-backend/test');
    });

    it('先頭スラッシュがないパスを処理できること', () => {
      expect(serverClient.resolveUrl('test')).toBe('http://test-backend/test');
    });

    it('BACKEND_URL が未設定なら http://backend:8080 を使うこと', () => {
      delete process.env.BACKEND_URL;
      expect(serverClient.resolveUrl('/test')).toBe('http://backend:8080/test');
    });
  });

  describe('getHeaders', () => {
    it('デフォルトヘッダーを返すこと', async () => {
      const headers = await serverClient.getHeaders();
      expect(headers).toEqual({
        'Content-Type': 'application/json',
      });
    });

    it('カスタムヘッダーをマージすること', async () => {
      const headers = await serverClient.getHeaders({ 'Custom-Header': 'value' });
      expect(headers).toHaveProperty('Custom-Header', 'value');
    });
  });

  describe('get', () => {
    it('呼び出し元が渡したヘッダーを付けて fetch を呼び出すこと', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        json: async () => ({ data: 'ok' }),
      });
      const result = await serverClient.get('/test', { headers: { 'X-Store-ID': '123' } });

      expect(global.fetch).toHaveBeenCalledWith(
        'http://test-backend/test',
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-Store-ID': '123',
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

    it('失敗の例外が応答の状態コードを持つこと', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      });

      const error: unknown = await serverClient.get('/test').catch(e => e);
      expect(error).toBeInstanceOf(ServerApiError);
      expect((error as ServerApiError).status).toBe(404);
    });
  });
});
