import { getAppDomain, isStoreDomain } from '../config';

const clearCookie = (name: string) => {
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT`;
};

describe('config', () => {
  beforeEach(() => {
    clearCookie('x-mw-app-domain');
    clearCookie('x-mw-role');
  });

  afterEach(() => {
    clearCookie('x-mw-app-domain');
    clearCookie('x-mw-role');
  });

  describe('getAppDomain', () => {
    it('proxy が立てた cookie の値を返す', () => {
      // 実行時に決まる値であることの担保。ビルド時に焼き込まれた定数を読んでいると
      // この値は反映されず、本番で解決しないドメインを指したまま気付けない
      document.cookie = 'x-mw-app-domain=kizuna.jp';

      expect(getAppDomain()).toBe('kizuna.jp');
    });

    it('cookie が無ければ開発既定値へ落ちる', () => {
      expect(getAppDomain()).toBe('kizuna.test');
    });
  });

  describe('isStoreDomain', () => {
    it('proxy が role=store を立てていれば true', () => {
      document.cookie = 'x-mw-role=store';

      expect(isStoreDomain()).toBe(true);
    });

    it('アプリドメインでは false', () => {
      document.cookie = 'x-mw-role=app';

      expect(isStoreDomain()).toBe(false);
    });

    it('アプリドメインが開発既定値と違ってもアプリドメインと判定する', () => {
      // ドメイン文字列の比較だと、本番ホスト(例: kizuna.jp)と既定値(kizuna.test)が
      // 食い違ってアプリドメイン上でも店舗ドメイン扱いになる
      document.cookie = 'x-mw-role=app';
      document.cookie = 'x-mw-app-domain=kizuna.jp';

      expect(isStoreDomain()).toBe(false);
    });

    it('role cookie が無ければ false（店舗ドメインと誤判定しない）', () => {
      expect(isStoreDomain()).toBe(false);
    });
  });
});
