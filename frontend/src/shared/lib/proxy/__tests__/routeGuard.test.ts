import { NextRequest, NextResponse } from 'next/server';
import { handleRouteProtection } from '../routeGuard';

// Mock NextResponse.redirect
jest.mock('next/server', () => {
  return {
    NextResponse: {
      redirect: jest.fn(url => ({
        status: 307,
        headers: { get: () => url.toString() },
        cookies: { set: jest.fn() },
      })),
    },
  };
});

const createRequest = (
  path: string,
  hasToken: boolean,
  cookies: Record<string, string> = {},
  search = ''
) => {
  return {
    nextUrl: {
      pathname: path,
      search,
    },
    url: 'http://localhost' + path,
    cookies: {
      has: (name: string) => (name === 'token' ? hasToken : Object.hasOwn(cookies, name)),
      get: (name: string) =>
        cookies[name] !== undefined ? { name, value: cookies[name] } : undefined,
    },
  } as unknown as NextRequest;
};

describe('routeGuard', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('redirects to /auth/login when accessing /admin without token', () => {
    const req = createRequest('/admin/dashboard', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/auth/login' })
    );
    expect(res).not.toBeNull();
  });

  it('allows access to /admin with token', () => {
    const req = createRequest('/admin/dashboard', true);
    const res = handleRouteProtection(req, 'app');

    expect(res).toBeNull();
  });

  it('allows access to /auth/login without token (public route)', () => {
    const req = createRequest('/auth/login', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to /auth/invite without token (public route)', () => {
    const req = createRequest('/auth/invite', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to the legacy /auth/invite/:token without token (public route)', () => {
    // 配布済みリンクの受け口。守衛に弾かれるとフラグメント形式へ送り直す前に落ちる
    const req = createRequest('/auth/invite/abc123', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to /auth/line/callback without token (public route)', () => {
    const req = createRequest('/auth/line/callback', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows a member-console session on /auth/line/callback (link flow)', () => {
    const req = createRequest('/auth/line/callback', true, { 'session-kind': 'member' });
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('redirects to / when accessing /store without token', () => {
    const req = createRequest('/store/orders', false);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).toHaveBeenCalledWith(expect.objectContaining({ pathname: '/' }));
    expect(res).not.toBeNull();
  });

  it('allows access to an id-less /store route with token', () => {
    const req = createRequest('/store/orders', true, { 'session-kind': 'admin' });
    const res = handleRouteProtection(req, 'store');

    expect(res).toBeNull();
  });

  it('treats /platform/** as an ordinary unknown path without redirecting', () => {
    for (const path of ['/platform', '/platform/login', '/platform/dashboard']) {
      for (const hasToken of [false, true]) {
        expect(handleRouteProtection(createRequest(path, hasToken), 'app')).toBeNull();
      }
    }
    expect(NextResponse.redirect).not.toHaveBeenCalled();
  });

  it('redirects the retired /store/entry to /admin/dashboard', () => {
    const req = createRequest('/store/entry', true, { 'session-kind': 'admin' });
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/admin/dashboard' })
    );
    expect(res).not.toBeNull();
  });

  it('redirects a legacy id-bearing store path to the id-less path and moves the id to the cookie', () => {
    const req = createRequest(
      '/store/5/orders/3/edit',
      true,
      { 'session-kind': 'admin' },
      '?tab=a'
    );
    const res = handleRouteProtection(req, 'store') as unknown as {
      cookies: { set: jest.Mock };
    };

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/store/orders/3/edit', search: '?tab=a' })
    );
    expect(res.cookies.set).toHaveBeenCalledWith('current-store-id', '5', { path: '/' });
  });

  it.each(['/store/5', '/store/5/', '/store/5/dashboard', '/store/5/select'])(
    'redirects a legacy store path without a live page (%s) to /admin/dashboard with the cookie',
    path => {
      const res = handleRouteProtection(
        createRequest(path, true, { 'session-kind': 'admin' }),
        'store'
      ) as unknown as { cookies: { set: jest.Mock } };

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/admin/dashboard' })
      );
      expect(res.cookies.set).toHaveBeenCalledWith('current-store-id', '5', { path: '/' });
    }
  );

  it('redirects the retired store account settings to the single account settings page', () => {
    handleRouteProtection(
      createRequest('/store/5/settings/account', true, { 'session-kind': 'admin' }),
      'store'
    );

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/admin/settings/account' })
    );
  });

  it('sends a legacy store path without a token to / (no cookie rewrite)', () => {
    const res = handleRouteProtection(createRequest('/store/5/orders', false), 'store');

    expect(NextResponse.redirect).toHaveBeenCalledWith(expect.objectContaining({ pathname: '/' }));
    expect((res as unknown as { cookies: { set: jest.Mock } }).cookies.set).not.toHaveBeenCalled();
  });

  it('redirects to /auth/login when accessing /cast without token', () => {
    const req = createRequest('/cast/schedule', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/auth/login' })
    );
    expect(res).not.toBeNull();
  });

  it('allows access to /cast with token', () => {
    const req = createRequest('/cast/schedule', true);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('redirects to /auth/login when accessing /member without token', () => {
    const req = createRequest('/member', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/auth/login' })
    );
    expect(res).not.toBeNull();
  });

  it('persists the member return path (with query) on the unauthenticated redirect', () => {
    // 差し戻しはサーバ側で起きるため、クライアントの画面は戻り先を覚えられない。
    // リダイレクト応答の cookie が唯一の持ち越し手段であることを固定する。
    const req = createRequest('/member/reservations/new', false, {}, '?store=demo.kizuna.test');
    const res = handleRouteProtection(req, 'app') as unknown as {
      cookies: { set: jest.Mock };
    };

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/auth/login' })
    );
    expect(res.cookies.set).toHaveBeenCalledWith(
      'member-return-path',
      '/member/reservations/new?store=demo.kizuna.test',
      { path: '/' }
    );
  });

  it('does not persist a return path that fails the whitelist', () => {
    // /member 単独は白名単（/member/ 配下）を通らない。危険な文字を含むクエリも保存しない。
    const bare = handleRouteProtection(createRequest('/member', false), 'app') as unknown as {
      cookies: { set: jest.Mock };
    };
    expect(bare.cookies.set).not.toHaveBeenCalled();

    const unsafe = handleRouteProtection(
      createRequest('/member/reservations/new', false, {}, '?store=<script>'),
      'app'
    ) as unknown as { cookies: { set: jest.Mock } };
    expect(unsafe.cookies.set).not.toHaveBeenCalled();
  });

  it.each(['/member/receipts', '/member/receipts/'])(
    'renders %s without a token (the fragment carries the receipt token)',
    path => {
      // 伝票トークンは QR の URL のフラグメントで届き、サーバへは送られない。ここで差し戻すと
      // 画面が一度も描かれないままトークンが失われ、ログイン後に戻っても申領できない。
      const res = handleRouteProtection(createRequest(path, false), 'app');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    }
  );

  it.each(['admin', 'cast'])(
    'renders the receipt claim route for a %s console session (the fragment must reach the client)',
    consoleValue => {
      // 別コンソールのセッションが残ったまま QR を開く場面。ここでコンソール整合で差し戻すと、
      // 免除の目的（フラグメントをブラウザで受け取ってから預ける）が果たせない
      const res = handleRouteProtection(
        createRequest('/member/receipts', true, { 'session-kind': consoleValue }),
        'app'
      );

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    }
  );

  it('still redirects an admin session away from other /member paths', () => {
    const res = handleRouteProtection(
      createRequest('/member/visits', true, { 'session-kind': 'admin' }),
      'app'
    );

    expect(NextResponse.redirect).toHaveBeenCalled();
    expect(res).not.toBeNull();
  });

  it('still bounces other /member paths without a token', () => {
    // 免除は申領画面ちょうどだけ。前綴で緩めると会員ポータル全体が未認証で描かれる
    const res = handleRouteProtection(createRequest('/member/receipts/other', false), 'app');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/auth/login' })
    );
    expect(res).not.toBeNull();
  });

  it('allows access to /member with token', () => {
    const req = createRequest('/member', true);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to /auth/register without token (public route)', () => {
    const req = createRequest('/auth/register', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to /auth/initial-setup without token', () => {
    const req = createRequest('/auth/initial-setup', false);
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows /auth/initial-setup with a token left over from a rebuilt database', () => {
    const req = createRequest('/auth/initial-setup', true, { 'session-kind': 'admin' });
    const res = handleRouteProtection(req, 'app');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('does not redirect the public /casts route without token', () => {
    const req = createRequest('/casts', false);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('does not redirect the public /casts/:id route without token', () => {
    const req = createRequest('/casts/abc', false);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  describe('console/area alignment', () => {
    it('redirects an admin session on /cast/* to /admin/dashboard', () => {
      const req = createRequest('/cast/schedule', true, { 'session-kind': 'admin' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/admin/dashboard' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a cast-console session on /admin/* to /cast/schedule', () => {
      const req = createRequest('/admin/dashboard', true, { 'session-kind': 'cast' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/cast/schedule' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a cast-console session on /store/* to /cast/schedule', () => {
      const req = createRequest('/store/orders', true, { 'session-kind': 'cast' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/cast/schedule' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a member-console session on /admin/* to /member', () => {
      const req = createRequest('/admin/dashboard', true, { 'session-kind': 'member' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/member' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a member-console session on a legacy /store/* path to /member', () => {
      const req = createRequest('/store/5/orders', true, { 'session-kind': 'member' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/member' })
      );
      expect(res).not.toBeNull();
    });

    it('allows a member-console session on /member', () => {
      const req = createRequest('/member', true, { 'session-kind': 'member' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('redirects a cast-console session on /member/* to /cast/schedule', () => {
      const req = createRequest('/member', true, { 'session-kind': 'cast' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/cast/schedule' })
      );
      expect(res).not.toBeNull();
    });

    it.each(['/admin/dashboard', '/store/orders'])(
      'allows an admin session on %s (one admin screen spans both prefixes)',
      path => {
        const res = handleRouteProtection(
          createRequest(path, true, { 'session-kind': 'admin' }),
          'app'
        );

        expect(NextResponse.redirect).not.toHaveBeenCalled();
        expect(res).toBeNull();
      }
    );

    it('allows a cast-console session on the public /auth/login route', () => {
      const req = createRequest('/auth/login', true, { 'session-kind': 'cast' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('does not guard when the console cookie is absent (legacy session)', () => {
      const req = createRequest('/admin/dashboard', true);
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it.each(['platform', 'store'])(
      'does not guard a retired console cookie value (%s)',
      consoleValue => {
        const req = createRequest('/cast/schedule', true, { 'session-kind': consoleValue });
        const res = handleRouteProtection(req, 'app');

        expect(NextResponse.redirect).not.toHaveBeenCalled();
        expect(res).toBeNull();
      }
    );

    it('does not crash or guard on a prototype-chain cookie value', () => {
      const req = createRequest('/admin/dashboard', true, { 'session-kind': 'constructor' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('allows a cast-console session on the shared /admin/settings/account route', () => {
      const req = createRequest('/admin/settings/account', true, { 'session-kind': 'cast' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('still guards a cast-console session on the non-shared /admin/settings hub', () => {
      const req = createRequest('/admin/settings', true, { 'session-kind': 'cast' });
      const res = handleRouteProtection(req, 'app');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/cast/schedule' })
      );
      expect(res).not.toBeNull();
    });
  });

  it('ignores other routes', () => {
    const req = createRequest('/public/page', false);
    const res = handleRouteProtection(req, 'store'); // Role doesn't strictly matter for non-protected routes in current implementation

    expect(res).toBeNull();
  });
});
