import { storePath } from '../store-route';

describe('store-route', () => {
  describe('storePath', () => {
    it('subPath を /store の直後へ連結する', () => {
      expect(storePath('/casts/create')).toBe('/store/casts/create');
    });
  });
});
