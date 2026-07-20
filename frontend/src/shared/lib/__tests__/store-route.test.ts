import { getStoreIdFromPath, replaceStoreIdInPath } from '../store-route';

describe('store-route', () => {
  describe('getStoreIdFromPath', () => {
    it('数値idを含むpathからstoreIdを解決する', () => {
      expect(getStoreIdFromPath('/store/123/dashboard')).toBe('123');
    });

    it('storeIdの直後がpath終端でも解決する', () => {
      expect(getStoreIdFromPath('/store/123')).toBe('123');
    });

    it('末尾スラッシュ付きでも解決する', () => {
      expect(getStoreIdFromPath('/store/123/')).toBe('123');
    });

    it('数値以外のsegmentはundefinedを返す', () => {
      expect(getStoreIdFromPath('/store/abc')).toBeUndefined();
    });

    it('/store/selectはundefinedを返す', () => {
      expect(getStoreIdFromPath('/store/select')).toBeUndefined();
    });

    it('/store単体はundefinedを返す', () => {
      expect(getStoreIdFromPath('/store')).toBeUndefined();
    });

    it('/platform配下はundefinedを返す', () => {
      expect(getStoreIdFromPath('/platform/dashboard')).toBeUndefined();
    });
  });

  describe('replaceStoreIdInPath', () => {
    it('既存のstoreIdを新しいidへ置換する', () => {
      expect(replaceStoreIdInPath('/store/123/orders', 456)).toBe('/store/456/orders');
    });

    it('storeId未確定のstore配下pathにはstoreIdを挿入する', () => {
      expect(replaceStoreIdInPath('/store/orders', 456)).toBe('/store/456/orders');
    });

    it('store以外のpathにはstoreId付きdashboardへフォールバックする', () => {
      expect(replaceStoreIdInPath('/platform/dashboard', 456)).toBe('/store/456/dashboard');
    });
  });
});
