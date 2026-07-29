import {
  getStoreIdFromPath,
  isLegacyStorePath,
  isRetiredStorePath,
  isStoreEntryPath,
  replaceStoreIdInPath,
  resolveStoreHref,
  storePath,
  storeEntryPath,
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

    it('/store/entryはundefinedを返す', () => {
      expect(getStoreIdFromPath('/store/entry')).toBeUndefined();
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

    it('store以外のpathからは入口ルートへフォールバックする', () => {
      // 着地先はメニュー由来のため、店舗スコープ外からの切替は入口ルートに委ねる。
      expect(replaceStoreIdInPath('/platform/dashboard', 456)).toBe('/store/entry');
    });

    it('/store/entryは店舗sub-pathとして扱わず入口ルートのままにする', () => {
      // /store/entry は storeId を含まない静的ルート。sub-path 保存だと実在しない
      // /store/456/entry を生む。
      expect(replaceStoreIdInPath('/store/entry', 456)).toBe('/store/entry');
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

  describe('storeEntryPath', () => {
    it('nextなしは入口ルートを返す', () => {
      expect(storeEntryPath()).toBe('/store/entry');
    });

    it('nextありはencodeされたnextクエリ付きで返す', () => {
      expect(storeEntryPath('/store/orders')).toBe('/store/entry?next=%2Fstore%2Forders');
    });
  });

  describe('resolveStoreHref', () => {
    it('/store以外のpathは無加工で通す', () => {
      expect(resolveStoreHref('/platform/stores', '2')).toBe('/platform/stores');
    });

    it('storeId確定時は/storeの直後にidを挿入する', () => {
      expect(resolveStoreHref('/store/orders', '2')).toBe('/store/2/orders');
    });

    it('storeId未確定時は入口ルート（next保存）へ誘導する', () => {
      expect(resolveStoreHref('/store/orders', undefined)).toBe(
        '/store/entry?next=%2Fstore%2Forders'
      );
    });

    // 入口ルートは storeId を持たない静的ルート。ここでidを埋めると、メニュー障害時に
    // サイドバーが出す唯一の店舗コンソール導線が /store/{id}/entry になって 404 になる。
    it('入口ルートにはstoreIdを埋めない', () => {
      expect(resolveStoreHref('/store/entry', '2')).toBe('/store/entry');
      expect(resolveStoreHref('/store/entry?next=%2Fstore%2Forders', '2')).toBe(
        '/store/entry?next=%2Fstore%2Forders'
      );
    });
  });

  describe('isLegacyStorePath', () => {
    it('id無しの店舗レガシーpathはtrue', () => {
      expect(isLegacyStorePath('/store/orders')).toBe(true);
    });

    // 入口ルート自身がレガシー判定に掛かると、守衛の差し戻し先も入口になって
    // 無限リダイレクトになる。この負向断言がその唯一の守りなので消さないこと。
    it('/store/entry 自身はレガシー扱いしない（自己リダイレクト循環の防止）', () => {
      expect(isLegacyStorePath('/store/entry')).toBe(false);
      expect(isLegacyStorePath('/store/entry/')).toBe(false);
    });

    it('entry を接頭辞に持つだけの別pathはレガシー扱いする', () => {
      expect(isLegacyStorePath('/store/entrypoint')).toBe(true);
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

  describe('isStoreEntryPath', () => {
    it('入口ルート自身・配下・クエリ付きはtrue', () => {
      expect(isStoreEntryPath('/store/entry')).toBe(true);
      expect(isStoreEntryPath('/store/entry/')).toBe(true);
      expect(isStoreEntryPath('/store/entry/foo')).toBe(true);
      expect(isStoreEntryPath('/store/entry?next=%2Fstore%2Forders')).toBe(true);
    });

    it('entry を接頭辞に持つだけの別pathはfalse', () => {
      expect(isStoreEntryPath('/store/entrypoint')).toBe(false);
    });
  });

  describe('isRetiredStorePath', () => {
    // 廃止済みルートを next として復元すると /store/{id}/select 等へ解決して 404 になる。
    it('廃止済みの店舗選択・店舗ダッシュボードはtrue', () => {
      expect(isRetiredStorePath('/store/select')).toBe(true);
      expect(isRetiredStorePath('/store/dashboard')).toBe(true);
      expect(isRetiredStorePath('/store/dashboard/')).toBe(true);
    });

    // 移設後の正規 URL だったためブックマークに残る。レガシー判定は数値id配下を除外するので、
    // ここで拾わないと守衛に収容されず素の 404 になる。
    it('数値id付きの旧ダッシュボードもtrue', () => {
      expect(isRetiredStorePath('/store/5/dashboard')).toBe(true);
      expect(isRetiredStorePath('/store/5/dashboard/')).toBe(true);
    });

    it('接頭辞が一致するだけの現役pathはfalse', () => {
      expect(isRetiredStorePath('/store/selections')).toBe(false);
      expect(isRetiredStorePath('/store/orders')).toBe(false);
    });

    it('現役の数値id配下はfalse', () => {
      expect(isRetiredStorePath('/store/5/orders')).toBe(false);
    });
  });
});
