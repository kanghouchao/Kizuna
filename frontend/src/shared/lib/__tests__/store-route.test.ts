import {
  getStoreIdFromPath,
  isLegacyStorePath,
  replaceStoreIdInPath,
  resolveStoreHref,
  storePath,
  storeSelectPath,
} from '../store-route';

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

    it('/store/selectは店舗sub-pathではなくstoreId付きdashboardへフォールバックする（#413 Fix5-2）', () => {
      // /store/select は storeId を含まない静的ルート。sub-path 保存だと実在しない
      // /store/456/select を生むため dashboard へ振り分ける。
      expect(replaceStoreIdInPath('/store/select', 456)).toBe('/store/456/dashboard');
    });
  });

  describe('storePath', () => {
    it('storeIdとsub-pathを結合して店舗ルートを組む', () => {
      expect(storePath('5', '/casts/create')).toBe('/store/5/casts/create');
    });

    it('sub-pathが単一segmentでも結合する', () => {
      expect(storePath('5', '/orders')).toBe('/store/5/orders');
    });
  });

  describe('storeSelectPath', () => {
    it('nextなしは店舗選択ルートを返す', () => {
      expect(storeSelectPath()).toBe('/store/select');
    });

    it('nextありはencodeされたnextクエリ付きで返す', () => {
      expect(storeSelectPath('/store/orders')).toBe('/store/select?next=%2Fstore%2Forders');
    });
  });

  describe('resolveStoreHref', () => {
    it('/store以外のpathは無加工で通す', () => {
      expect(resolveStoreHref('/platform/stores', '2')).toBe('/platform/stores');
    });

    it('storeId確定時は/storeの直後にidを挿入する', () => {
      expect(resolveStoreHref('/store/orders', '2')).toBe('/store/2/orders');
    });

    it('storeId未確定時は店舗選択ルート（next保存）へ誘導する', () => {
      expect(resolveStoreHref('/store/orders', undefined)).toBe(
        '/store/select?next=%2Fstore%2Forders'
      );
    });
  });

  describe('isLegacyStorePath', () => {
    it('id無しの店舗レガシーpathはtrue', () => {
      expect(isLegacyStorePath('/store/orders')).toBe(true);
    });

    it('/store/selectはfalse', () => {
      expect(isLegacyStorePath('/store/select')).toBe(false);
    });

    it('数値id配下はfalse', () => {
      expect(isLegacyStorePath('/store/5/orders')).toBe(false);
      expect(isLegacyStorePath('/store/5')).toBe(false);
    });

    it('/store単体はfalse', () => {
      expect(isLegacyStorePath('/store')).toBe(false);
    });

    it('/platform配下はfalse', () => {
      expect(isLegacyStorePath('/platform/dashboard')).toBe(false);
    });
  });
});
