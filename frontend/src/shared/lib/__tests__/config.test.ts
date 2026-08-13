import { getPlatformDomain, isStoreDomain } from '../config';

const clearCookie = (name: string) => {
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT`;
};

describe('config', () => {
  beforeEach(() => {
    clearCookie('x-mw-platform-domain');
    clearCookie('x-mw-role');
  });

  afterEach(() => {
    clearCookie('x-mw-platform-domain');
    clearCookie('x-mw-role');
  });

  describe('getPlatformDomain', () => {
    it('proxy が立てた cookie の値を返す', () => {
      // 実行時に決まる値であることの担保。ビルド時に焼き込まれた定数を読んでいると
      // この値は反映されず、本番で解決しないドメインを指したまま気付けない
      document.cookie = 'x-mw-platform-domain=kizuna.jp';

      expect(getPlatformDomain()).toBe('kizuna.jp');
    });

    it('cookie が無ければ開発既定値へ落ちる', () => {
      expect(getPlatformDomain()).toBe('kizuna.test');
    });
  });

  describe('isStoreDomain', () => {
    it('proxy が role=store を立てていれば true', () => {
      document.cookie = 'x-mw-role=store';

      expect(isStoreDomain()).toBe(true);
    });

    it('平台ドメインでは false', () => {
      document.cookie = 'x-mw-role=platform';

      expect(isStoreDomain()).toBe(false);
    });

    it('平台ドメインが開発既定値と違っても平台と判定する', () => {
      // ドメイン文字列の比較だと、本番ホスト(例: kizuna.jp)と既定値(kizuna.test)が
      // 食い違って平台ドメイン上でも店舗ドメイン扱いになる
      document.cookie = 'x-mw-role=platform';
      document.cookie = 'x-mw-platform-domain=kizuna.jp';

      expect(isStoreDomain()).toBe(false);
    });

    it('role cookie が無ければ false（店舗ドメインと誤判定しない）', () => {
      expect(isStoreDomain()).toBe(false);
    });
  });
});
