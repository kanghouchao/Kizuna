import { isStorePath, storeIdOf, storePath } from '../store-route';

describe('store-route', () => {
  describe('storePath', () => {
    it('店舗 id と subPath を /store の後へ連結する', () => {
      expect(storePath(12, '/casts/create')).toBe('/store/12/casts/create');
      expect(storePath('3', '/dashboard')).toBe('/store/3/dashboard');
    });
  });

  describe('storeIdOf', () => {
    it.each([
      ['/store/12', '12'],
      ['/store/12/', '12'],
      ['/store/12/orders/5/edit', '12'],
    ])('%s の店舗 id は %s', (pathname, id) => {
      expect(storeIdOf(pathname)).toBe(id);
    });

    it.each([
      '/store',
      '/store/',
      '/store/abc/orders',
      '/store/12abc',
      '/storefront/12',
      '/admin/12',
    ])('%s は店舗 id を持たない', pathname => {
      expect(storeIdOf(pathname)).toBeUndefined();
    });
  });

  describe('isStorePath', () => {
    it.each(['/store', '/store/', '/store/12', '/store/12/casts/create'])(
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
