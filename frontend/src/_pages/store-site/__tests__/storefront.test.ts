import { storefrontService } from '../api/storefront';
import { serverClient } from '@/shared/api/index.server';

// serverClientのモック
jest.mock('@/shared/api/server-client', () => ({
  serverClient: {
    get: jest.fn(),
  },
}));

describe('storefrontService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('fetchCasts', () => {
    it('serverClient.get を呼び出すこと', async () => {
      const mockCasts = [{ id: '1', name: 'Cast 1' }];
      (serverClient.get as jest.Mock).mockResolvedValue(mockCasts);

      const result = await storefrontService.fetchCasts();

      expect(serverClient.get).toHaveBeenCalledWith('/tenant/casts/public', expect.any(Object));
      expect(result).toEqual(mockCasts);
    });

    it('エラー時に空配列を返すこと', async () => {
      (serverClient.get as jest.Mock).mockRejectedValue(new Error('Failed'));
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

      const result = await storefrontService.fetchCasts();

      expect(result).toEqual([]);
      expect(consoleSpy).toHaveBeenCalled();
      consoleSpy.mockRestore();
    });
  });

  describe('fetchCast', () => {
    it('公開キャスト一覧から id で 1 件返すこと', async () => {
      const mockCasts = [
        { id: '1', name: 'Cast 1' },
        { id: '2', name: 'Cast 2' },
      ];
      (serverClient.get as jest.Mock).mockResolvedValue(mockCasts);

      const result = await storefrontService.fetchCast('2');

      expect(result?.name).toBe('Cast 2');
    });

    it('存在しない id は null を返すこと', async () => {
      (serverClient.get as jest.Mock).mockResolvedValue([{ id: '1', name: 'Cast 1' }]);

      const result = await storefrontService.fetchCast('missing');

      expect(result).toBeNull();
    });
  });

  describe('fetchSiteConfig', () => {
    it('serverClient.get を呼び出すこと', async () => {
      const mockConfig = { mv_type: 'video', logo_url: 'logo.png' };
      (serverClient.get as jest.Mock).mockResolvedValue(mockConfig);

      const result = await storefrontService.fetchSiteConfig();

      expect(serverClient.get).toHaveBeenCalledWith('/tenant/config/public', expect.any(Object));
      expect(result).toEqual(mockConfig);
    });

    it('エラー時にデフォルト設定を返すこと', async () => {
      (serverClient.get as jest.Mock).mockRejectedValue(new Error('Failed'));
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

      const result = await storefrontService.fetchSiteConfig();

      expect(result).toEqual({ mv_type: 'image' });
      expect(consoleSpy).toHaveBeenCalled();
      consoleSpy.mockRestore();
    });
  });

  describe('fetchShifts', () => {
    it('serverClient.get を呼び出すこと', async () => {
      const mockShifts = [
        {
          cast_id: '1',
          cast_name: 'Cast 1',
          start_time: '20:00:00',
          end_time: '02:00:00',
        },
      ];
      (serverClient.get as jest.Mock).mockResolvedValue(mockShifts);

      const result = await storefrontService.fetchShifts();

      expect(serverClient.get).toHaveBeenCalledWith('/tenant/shifts/public', {
        cache: 'no-store',
      });
      expect(result).toEqual(mockShifts);
    });

    it('エラー時に空配列を返すこと', async () => {
      (serverClient.get as jest.Mock).mockRejectedValue(new Error('Failed'));
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

      const result = await storefrontService.fetchShifts();

      expect(result).toEqual([]);
      expect(consoleSpy).toHaveBeenCalled();
      consoleSpy.mockRestore();
    });
  });

  describe('getPageData', () => {
    it('データを集約して返すこと', async () => {
      const mockCasts = [{ id: '1', name: 'Cast 1' }];
      const mockConfig = { mv_type: 'image' };

      (serverClient.get as jest.Mock)
        .mockResolvedValueOnce(mockCasts) // 1回目の呼び出し: キャスト
        .mockResolvedValueOnce(mockConfig); // 2回目の呼び出し: 設定

      const result = await storefrontService.getPageData();

      expect(result.casts).toEqual(mockCasts);
      expect(result.siteConfig).toEqual(mockConfig);
    });
  });
});
