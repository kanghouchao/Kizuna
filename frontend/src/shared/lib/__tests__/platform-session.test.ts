import Cookies from 'js-cookie';
import {
  clearPlatformSession,
  getPlatformConsole,
  getPlatformStoreId,
  isPlatformSession,
  isStoreConsole,
  setPlatformStore,
  isSafeMemberReturnPath,
  rememberMemberReturnPath,
  startPlatformSession,
  takeMemberReturnPath,
} from '../platform-session';

describe('platform-session', () => {
  afterEach(() => {
    clearPlatformSession();
  });

  it('startPlatformSession sets the platform-role cookie, readable via getPlatformConsole', () => {
    startPlatformSession('platform');
    expect(getPlatformConsole()).toBe('platform');
  });

  it('setPlatformStore sets the platform-store-id cookie, readable via getPlatformStoreId', () => {
    setPlatformStore(2);
    expect(getPlatformStoreId()).toBe('2');
  });

  it('isPlatformSession is true only when platform-role cookie is present', () => {
    expect(isPlatformSession()).toBe(false);
    startPlatformSession('store');
    expect(isPlatformSession()).toBe(true);
  });

  it('clearPlatformSession removes both cookies', () => {
    startPlatformSession('platform');
    setPlatformStore(1);
    clearPlatformSession();
    expect(getPlatformConsole()).toBeUndefined();
    expect(getPlatformStoreId()).toBeUndefined();
  });

  it('isStoreConsole is true only for the store console value', () => {
    expect(isStoreConsole('store')).toBe(true);
    expect(isStoreConsole('platform')).toBe(false);
    expect(isStoreConsole('none')).toBe(false);
    // 旧形式（ロール名）の cookie が残っていても店舗文脈を確立しない（fail-closed）。
    expect(isStoreConsole('STORE_MANAGER')).toBe(false);
    expect(isStoreConsole(undefined)).toBe(false);
  });

  it('startPlatformSession sets the platform-role cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    startPlatformSession('platform', expiresAt);

    expect(setSpy).toHaveBeenCalledWith('platform-role', 'platform', {
      expires: new Date(expiresAt),
    });
    setSpy.mockRestore();
  });

  it('setPlatformStore sets the platform-store-id cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    setPlatformStore(2, expiresAt);

    expect(setSpy).toHaveBeenCalledWith('platform-store-id', '2', { expires: new Date(expiresAt) });
    setSpy.mockRestore();
  });
});

describe('会員ポータルの戻り先パス', () => {
  beforeEach(() => {
    Cookies.remove('member-return-path');
    sessionStorage.clear();
  });

  it('会員ポータル内の相対パスは覚えて取り出せること', () => {
    rememberMemberReturnPath('/member/reservations/new?store=store1.kizuna.test');

    expect(takeMemberReturnPath()).toBe('/member/reservations/new?store=store1.kizuna.test');
  });

  it('取り出しは 1 度きりで、2 度目は null になること', () => {
    rememberMemberReturnPath('/member/reservations/');

    expect(takeMemberReturnPath()).toBe('/member/reservations/');
    expect(takeMemberReturnPath()).toBeNull();
  });

  // 利用者側の値をそのまま遷移先にすると外部サイトへ飛ばせてしまうため、形ごと弾く
  it.each([
    ['絶対 URL', 'https://evil.test/member/'],
    ['スキーム相対', '//evil.test/member/'],
    ['スキーム入り', '/member/x:https://evil.test'],
    ['バックスラッシュ', '/member\\evil.test'],
    ['会員ポータル外', '/platform/dashboard/'],
    ['店舗コンソール', '/store/1/orders'],
    ['空文字', ''],
  ])('%s は覚えないこと', (_label, value) => {
    expect(isSafeMemberReturnPath(value)).toBe(false);
    rememberMemberReturnPath(value);
    expect(takeMemberReturnPath()).toBeNull();
  });

  it('cookie に直接仕込まれた危険な値も取り出さないこと', () => {
    Cookies.set('member-return-path', 'https://evil.test/');

    expect(takeMemberReturnPath()).toBeNull();
    expect(Cookies.get('member-return-path')).toBeUndefined();
  });

  // 伝票トークンはフラグメントで届く。cookie に入れるとサーバへ送られてしまうため、
  // 戻り先の断片だけはサーバへ渡らない置き場（sessionStorage）で往復させる
  it('フラグメントも一緒に覚えて、戻り先の末尾に添えて取り出せること', () => {
    rememberMemberReturnPath('/member/receipts', '#tok3n');

    expect(takeMemberReturnPath()).toBe('/member/receipts#tok3n');
  });

  it('フラグメントは cookie に載せないこと（サーバへ送られる場所に置かない）', () => {
    rememberMemberReturnPath('/member/receipts', '#tok3n');

    expect(Cookies.get('member-return-path')).toBe('/member/receipts');
  });

  it('フラグメントも 1 度きりで、2 度目には残らないこと', () => {
    rememberMemberReturnPath('/member/receipts', '#tok3n');
    takeMemberReturnPath();

    rememberMemberReturnPath('/member/points/');
    expect(takeMemberReturnPath()).toBe('/member/points/');
  });

  it('フラグメント無しで覚え直したら前のフラグメントは捨てること', () => {
    rememberMemberReturnPath('/member/receipts', '#tok3n');
    rememberMemberReturnPath('/member/points/');

    expect(takeMemberReturnPath()).toBe('/member/points/');
  });

  it('覚えない戻り先の要求は、既に覚えている組にも触れないこと', () => {
    // 401 の差し戻しは会員ポータルの外（LINE コールバック等）からも起きる。cookie と断片は
    // 対で 1 つの戻り先なので、片方だけ消すと「戻り先はあるがトークンが無い」画面に着地する
    rememberMemberReturnPath('/member/receipts', '#tok3n');

    rememberMemberReturnPath('/platform/line/callback', '');

    expect(takeMemberReturnPath()).toBe('/member/receipts#tok3n');
  });

  // 断片の置き場が使えないことは「元の画面へ戻れない」だけの不便で、呼び元（差し戻し）を
  // 止めてよい理由にはならない。投げると未認証の利用者がログインへも進めなくなる
  it('sessionStorage が投げても記憶と取り出しは例外を出さないこと', () => {
    const setItem = jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });
    const getItem = jest.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    const removeItem = jest.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });

    expect(() => rememberMemberReturnPath('/member/receipts', '#tok3n')).not.toThrow();
    expect(Cookies.get('member-return-path')).toBe('/member/receipts');
    expect(takeMemberReturnPath()).toBe('/member/receipts');

    setItem.mockRestore();
    getItem.mockRestore();
    removeItem.mockRestore();
  });

  it('# で始まらない値はフラグメントとして扱わないこと', () => {
    // 連結してもパスの一部にならないことを形で保証する（遷移先は利用者側の値から組み立てるため）
    rememberMemberReturnPath('/member/receipts', 'evil');

    expect(takeMemberReturnPath()).toBe('/member/receipts');
  });
});
