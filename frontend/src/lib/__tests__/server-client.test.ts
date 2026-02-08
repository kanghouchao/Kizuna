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
    it('should prepend backend url to path', () => {
      expect(serverClient.resolveUrl('/test')).toBe('http://test-backend/test');
    });

    it('should handle path without leading slash', () => {
      expect(serverClient.resolveUrl('test')).toBe('http://test-backend/test');
    });

    it('should fallback to localhost if env var missing', () => {
      delete process.env.TENANT_VALIDATION_API_URL;
      expect(serverClient.resolveUrl('/test')).toBe('http://backend:8080/test');
    });
  });

  describe('getHeaders', () => {
    it('should return default headers', async () => {
      mockCookieStore.get.mockReturnValue(undefined);
      const headers = await serverClient.getHeaders();
      expect(headers).toEqual({
        'Content-Type': 'application/json',
        'X-Role': 'tenant',
      });
    });

    it('should inject X-Tenant-ID from cookie', async () => {
      mockCookieStore.get.mockReturnValue({ value: '123' });
      const headers = await serverClient.getHeaders();
      expect(headers).toHaveProperty('X-Tenant-ID', '123');
    });

    it('should merge custom headers', async () => {
      mockCookieStore.get.mockReturnValue(undefined);
      const headers = await serverClient.getHeaders({ 'Custom-Header': 'value' });
      expect(headers).toHaveProperty('Custom-Header', 'value');
    });
  });

  describe('get', () => {
    it('should call fetch with correct url and headers', async () => {
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

    it('should throw error on non-ok response', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Server Error',
      });

      await expect(serverClient.get('/test')).rejects.toThrow('API Error: 500 Server Error');
    });
  });
});
