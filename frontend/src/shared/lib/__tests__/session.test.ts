import Cookies from 'js-cookie';
import {
  clearSession,
  getSessionKind,
  getCurrentStoreId,
  hasSession,
  setCurrentStore,
  isSafeMemberReturnPath,
  rememberMemberReturnPath,
  startSession,
  takeMemberReturnPath,
} from '../session';

describe('session', () => {
  afterEach(() => {
    clearSession();
  });

  it('startSession sets the session-kind cookie, readable via getSessionKind', () => {
    startSession('admin');
    expect(getSessionKind()).toBe('admin');
  });

  it('setCurrentStore sets the current-store-id cookie, readable via getCurrentStoreId', () => {
    setCurrentStore(2);
    expect(getCurrentStoreId()).toBe('2');
  });

  it('hasSession is true only when session-kind cookie is present', () => {
    expect(hasSession()).toBe(false);
    startSession('admin');
    expect(hasSession()).toBe(true);
  });

  it('clearSession removes both cookies', () => {
    startSession('admin');
    setCurrentStore(1);
    clearSession();
    expect(getSessionKind()).toBeUndefined();
    expect(getCurrentStoreId()).toBeUndefined();
  });

  it('startSession sets the session-kind cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    startSession('admin', expiresAt);

    expect(setSpy).toHaveBeenCalledWith('session-kind', 'admin', {
      expires: new Date(expiresAt),
    });
    setSpy.mockRestore();
  });

  it('setCurrentStore sets the current-store-id cookie with the same expiry as expiresAt', () => {
    const setSpy = jest.spyOn(Cookies, 'set');
    const expiresAt = Date.now() + 60_000;

    setCurrentStore(2, expiresAt);

    expect(setSpy).toHaveBeenCalledWith('current-store-id', '2', { expires: new Date(expiresAt) });
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
    ['会員ポータル外', '/admin/dashboard/'],
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

  it('別タブが cookie を書き替えていたら、このタブの断片は添えないこと', () => {
    // cookie はタブ間で共有、置き場はタブ内。組で持たないと、このタブの伝票トークンが
    // 別タブの用事で書かれた無関係なパスへ付いて回る
    rememberMemberReturnPath('/member/receipts', '#tok3n');
    Cookies.set('member-return-path', '/member/points/');

    expect(takeMemberReturnPath()).toBe('/member/points/');
  });

  it('覚えない戻り先の要求は、既に覚えている組にも触れないこと', () => {
    // 401 の差し戻しは会員ポータルの外（LINE コールバック等）からも起きる。cookie と断片は
    // 対で 1 つの戻り先なので、片方だけ消すと「戻り先はあるがトークンが無い」画面に着地する
    rememberMemberReturnPath('/member/receipts', '#tok3n');

    rememberMemberReturnPath('/auth/line/callback', '');

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
