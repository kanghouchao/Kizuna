import { isLegacyStorePath, splitLegacyStorePath, storePath } from '../store-route';

describe('store-route', () => {
  describe('storePath', () => {
    it('subPath を /store の直後へ連結する', () => {
      expect(storePath('/casts/create')).toBe('/store/casts/create');
    });
  });

  describe('isLegacyStorePath', () => {
    it.each(['/store/5', '/store/5/', '/store/123/orders', '/store/7/settings/profile'])(
      '%s は店舗 id を持つ旧形式',
      path => {
        expect(isLegacyStorePath(path)).toBe(true);
      }
    );

    it.each(['/store', '/store/orders', '/store/entry', '/store/5abc', '/admin/5/orders'])(
      '%s は旧形式ではない',
      path => {
        expect(isLegacyStorePath(path)).toBe(false);
      }
    );
  });

  describe('splitLegacyStorePath', () => {
    it('店舗 id と id 以降の subPath に分ける', () => {
      expect(splitLegacyStorePath('/store/12/orders/3/edit')).toEqual({
        storeId: '12',
        subPath: '/orders/3/edit',
      });
    });

    it('id 直後で終わる場合の subPath は空文字', () => {
      expect(splitLegacyStorePath('/store/12')).toEqual({ storeId: '12', subPath: '' });
    });

    it('旧形式でなければ undefined', () => {
      expect(splitLegacyStorePath('/store/orders')).toBeUndefined();
    });
  });
});
