import { storefrontService } from '../storefront';
import { serverClient } from '@/lib/server-client';

// Mock serverClient
jest.mock('@/lib/server-client', () => ({
  serverClient: {
    get: jest.fn(),
  },
}));

describe('storefrontService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('fetchCasts', () => {
    it('should call serverClient.get', async () => {
      const mockCasts = [{ id: '1', name: 'Cast 1' }];
      (serverClient.get as jest.Mock).mockResolvedValue(mockCasts);

      const result = await storefrontService.fetchCasts();

      expect(serverClient.get).toHaveBeenCalledWith('/tenant/casts/public', expect.any(Object));
      expect(result).toEqual(mockCasts);
    });

    it('should return empty array on error', async () => {
      (serverClient.get as jest.Mock).mockRejectedValue(new Error('Failed'));
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

      const result = await storefrontService.fetchCasts();

      expect(result).toEqual([]);
      expect(consoleSpy).toHaveBeenCalled();
      consoleSpy.mockRestore();
    });
  });

  describe('fetchSiteConfig', () => {
    it('should call serverClient.get', async () => {
      const mockConfig = { mv_type: 'video', logo_url: 'logo.png' };
      (serverClient.get as jest.Mock).mockResolvedValue(mockConfig);

      const result = await storefrontService.fetchSiteConfig();

      expect(serverClient.get).toHaveBeenCalledWith('/tenant/config/public', expect.any(Object));
      expect(result).toEqual(mockConfig);
    });

    it('should return default config on error', async () => {
      (serverClient.get as jest.Mock).mockRejectedValue(new Error('Failed'));
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

      const result = await storefrontService.fetchSiteConfig();

      expect(result).toEqual({ mv_type: 'image' });
      expect(consoleSpy).toHaveBeenCalled();
      consoleSpy.mockRestore();
    });
  });

  describe('getPageData', () => {
    it('should aggregate data', async () => {
      const mockCasts = [{ id: '1', name: 'Cast 1' }];
      const mockConfig = { mv_type: 'image' };

      (serverClient.get as jest.Mock)
        .mockResolvedValueOnce(mockCasts) // First call: casts
        .mockResolvedValueOnce(mockConfig); // Second call: config

      const result = await storefrontService.getPageData();

      expect(result.casts).toEqual(mockCasts);
      expect(result.siteConfig).toEqual(mockConfig);
    });
  });
});
