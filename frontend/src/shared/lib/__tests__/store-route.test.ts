import { isStorePath, storePath } from '../store-route';

describe('store-route', () => {
  describe('storePath', () => {
    it('subPath を /store の直後へ連結する', () => {
      expect(storePath('/casts/create')).toBe('/store/casts/create');
    });
  });

  describe('isStorePath', () => {
    it.each(['/store', '/store/', '/store/casts', '/store/casts/create'])(
      '%s は店舗級',
      pathname => {
        expect(isStorePath(pathname)).toBe(true);
      }
    );

    it.each([
      '/storefront',
      '/storefront/casts',
      '/stores',
      '/admin/dashboard',
      '/admin/store',
      '/',
    ])('%s は店舗級でない', pathname => {
      expect(isStorePath(pathname)).toBe(false);
    });
  });
});
