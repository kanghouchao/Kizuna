import redirectToLogin, {
  __resetNavigator,
  __setNavigatorForTests,
  currentReturnTarget,
  loginReturnTarget,
  resolveNext,
  withNext,
} from '../navigation';

describe('navigation helper', () => {
  afterEach(() => {
    __resetNavigator();
    jest.restoreAllMocks();
  });

  it('calls provided navigator with /auth/login when available', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    redirectToLogin();
    expect(navMock).toHaveBeenCalledWith('/auth/login');
  });

  it('理由を渡すと理由コードを載せたログイン画面へ送る', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    redirectToLogin({ reason: 'expired' });
    expect(navMock).toHaveBeenCalledWith('/auth/login?reason=expired');
  });

  it('returnHere なら今の画面を next と断片で預ける', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    window.history.pushState({}, '', '/member/receipts?s=1#tok3n');
    redirectToLogin({ reason: 'expired', returnHere: true });
    window.history.pushState({}, '', '/');
    expect(navMock).toHaveBeenCalledWith(
      '/auth/login?reason=expired&next=%2Fmember%2Freceipts%3Fs%3D1#tok3n'
    );
  });

  it('returnHere が無ければ今の画面を預けない', () => {
    const navMock = jest.fn();
    __setNavigatorForTests(navMock);
    window.history.pushState({}, '', '/member/receipts#tok3n');
    redirectToLogin();
    window.history.pushState({}, '', '/');
    expect(navMock).toHaveBeenCalledWith('/auth/login');
  });

  it('does not throw when provided navigator throws (caught)', () => {
    const badNav = jest.fn(() => {
      throw new Error('boom');
    });
    __setNavigatorForTests(badNav);
    expect(() => redirectToLogin()).not.toThrow();
  });
});

describe('resolveNext', () => {
  it.each([
    ['member', '/member/reservations/new?store=store1.kizuna.test'],
    ['member', '/member/receipts#tok3n'],
    ['admin', '/admin/staff'],
    ['admin', '/store/orders?q=%E5%B1%B1%E7%94%B0+%E8%8A%B1%E5%AD%90&sort=desc'],
    ['cast', '/cast/remunerations'],
  ] as const)('%s は本人種別の相対パス %s を戻り先にする', (kind, target) => {
    expect(resolveNext(kind, target)).toBe(target);
  });

  // 利用者側の値をそのまま遷移先にすると外部サイトへ飛ばせてしまうため、形ごと弾く
  it.each([
    ['絶対 URL', 'https://evil.test/member/'],
    ['スキーム相対', '//evil.test/member/'],
    ['パス中の //', '/member//evil.test'],
    ['スキーム入り', '/member/x:https://evil.test'],
    ['バックスラッシュ', '/member\\evil.test'],
    ['上位への移動', '/member/../admin/staff'],
    ['別の本人種別', '/admin/dashboard/'],
    ['接頭辞だけ似たパス', '/members/x'],
    ['空文字', ''],
  ])('%s は会員の戻り先にしない', (_label, target) => {
    expect(resolveNext('member', target)).toBeNull();
  });

  it('従業員は会員・キャストのパスへ戻らない', () => {
    expect(resolveNext('admin', '/member/receipts')).toBeNull();
    expect(resolveNext('admin', '/cast/')).toBeNull();
  });

  it('戻り先が無ければ null', () => {
    expect(resolveNext('member', undefined)).toBeNull();
  });
});

describe('withNext', () => {
  it('戻り先のパスは next に符号化して載せ、断片は URL の断片で持ち越す', () => {
    expect(withNext('/auth/login', '/member/reservations/new?store=s1#tok3n')).toBe(
      '/auth/login?next=%2Fmember%2Freservations%2Fnew%3Fstore%3Ds1#tok3n'
    );
  });

  it('理由と並べて載せられる', () => {
    expect(withNext('/auth/login', '/admin/staff', { reason: 'expired' })).toBe(
      '/auth/login?reason=expired&next=%2Fadmin%2Fstaff'
    );
  });

  it('戻り先が無ければ元のパスのまま', () => {
    expect(withNext('/auth/register', undefined)).toBe('/auth/register');
  });
});

describe('現在地からの戻り先', () => {
  afterEach(() => {
    window.history.pushState({}, '', '/');
  });

  it('本人種別の画面にいれば、パス・クエリ・断片をそのまま戻り先にする', () => {
    window.history.pushState({}, '', '/member/receipts?s=1#tok3n');

    expect(currentReturnTarget()).toBe('/member/receipts?s=1#tok3n');
  });

  // OAuth のコールバックのクエリ（認可コード）をログイン画面の URL へ写すとアクセスログに残る
  it('どの本人種別の画面でもなければ戻り先を持たない', () => {
    window.history.pushState({}, '', '/auth/line/callback?code=secret&state=s#frag');

    expect(currentReturnTarget()).toBeUndefined();
  });

  it('ログイン画面の next と断片を戻り先として読む', () => {
    window.history.pushState({}, '', '/auth/login?reason=expired&next=%2Fmember%2Freceipts#tok3n');

    expect(loginReturnTarget()).toBe('/member/receipts#tok3n');
  });

  it('next が無ければ断片があっても戻り先を持たない', () => {
    window.history.pushState({}, '', '/auth/login#tok3n');

    expect(loginReturnTarget()).toBeUndefined();
  });
});
