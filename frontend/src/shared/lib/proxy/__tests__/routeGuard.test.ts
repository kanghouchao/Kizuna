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

  it('redirects to /platform/login when accessing /platform without token', () => {
    const req = createRequest('/platform/dashboard', false);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/platform/login' })
    );
    expect(res).not.toBeNull();
  });

  it('allows access to /platform with token', () => {
    const req = createRequest('/platform/dashboard', true);
    const res = handleRouteProtection(req, 'platform');

    expect(res).toBeNull();
  });

  it('allows access to /platform/login without token (public route)', () => {
    const req = createRequest('/platform/login', false);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to /platform/invite without token (public route)', () => {
    const req = createRequest('/platform/invite', false);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to /platform/line/callback without token (public route)', () => {
    const req = createRequest('/platform/line/callback', false);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows a member-console session on /platform/line/callback (link flow)', () => {
    const req = createRequest('/platform/line/callback', true, { 'platform-role': 'member' });
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('redirects to / when accessing /store without token', () => {
    const req = createRequest('/store/orders', false);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).toHaveBeenCalledWith(expect.objectContaining({ pathname: '/' }));
    expect(res).not.toBeNull();
  });

  it('allows access to an id-scoped /store route with token', () => {
    const req = createRequest('/store/5/orders', true);
    const res = handleRouteProtection(req, 'store');

    expect(res).toBeNull();
  });

  it('allows access to /store/entry with token (no legacy redirect)', () => {
    const req = createRequest('/store/entry', true);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('redirects a legacy id-less /store path (with token) to /store/entry preserving next', () => {
    const req = createRequest('/store/orders', true);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({
        pathname: '/store/entry',
        search: '?next=%2Fstore%2Forders',
      })
    );
    expect(res).not.toBeNull();
  });

  it('redirects a retired id-bearing store path (with token) to /store/entry', () => {
    // /store/{id}/dashboard は本 PR 以前の正規の店舗着地 URL でブックマークに残るが、
    // 数値id配下はレガシー判定の対象外なので、廃止済み判定で拾わないと素の 404 になる。
    const req = createRequest('/store/5/dashboard', true);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/store/entry' })
    );
    expect(res).not.toBeNull();
  });

  it('does not treat a live id-bearing store path as retired', () => {
    const req = createRequest('/store/5/orders', true);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('redirects to /platform/login when accessing /cast without token', () => {
    const req = createRequest('/cast/schedule', false);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/platform/login' })
    );
    expect(res).not.toBeNull();
  });

  it('allows access to /cast with token', () => {
    const req = createRequest('/cast/schedule', true);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('redirects to /platform/login when accessing /member without token', () => {
    const req = createRequest('/member', false);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/platform/login' })
    );
    expect(res).not.toBeNull();
  });

  it('persists the member return path (with query) on the unauthenticated redirect', () => {
    // 差し戻しはサーバ側で起きるため、クライアントの画面は戻り先を覚えられない。
    // リダイレクト応答の cookie が唯一の持ち越し手段であることを固定する。
    const req = createRequest('/member/reservations/new', false, {}, '?store=demo.kizuna.test');
    const res = handleRouteProtection(req, 'platform') as unknown as {
      cookies: { set: jest.Mock };
    };

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: '/platform/login' })
    );
    expect(res.cookies.set).toHaveBeenCalledWith(
      'member-return-path',
      '/member/reservations/new?store=demo.kizuna.test',
      { path: '/' }
    );
  });

  it('does not persist a return path that fails the whitelist', () => {
    // /member 単独は白名単（/member/ 配下）を通らない。危険な文字を含むクエリも保存しない。
    const bare = handleRouteProtection(createRequest('/member', false), 'platform') as unknown as {
      cookies: { set: jest.Mock };
    };
    expect(bare.cookies.set).not.toHaveBeenCalled();

    const unsafe = handleRouteProtection(
      createRequest('/member/reservations/new', false, {}, '?store=<script>'),
      'platform'
    ) as unknown as { cookies: { set: jest.Mock } };
    expect(unsafe.cookies.set).not.toHaveBeenCalled();
  });

  it('allows access to /member with token', () => {
    const req = createRequest('/member', true);
    const res = handleRouteProtection(req, 'platform');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('allows access to /platform/register without token (public route)', () => {
    const req = createRequest('/platform/register', false);
    const res = handleRouteProtection(req, 'platform');

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
    it('redirects a store-console session on /platform/* to /store/entry', () => {
      const req = createRequest('/platform/dashboard', true, { 'platform-role': 'store' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/store/entry' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a store-console session on /cast/* to /store/entry', () => {
      const req = createRequest('/cast/schedule', true, { 'platform-role': 'store' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/store/entry' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a cast-console session on /platform/* to /cast/schedule', () => {
      const req = createRequest('/platform/dashboard', true, { 'platform-role': 'cast' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/cast/schedule' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a cast-console session on /store/* to /cast/schedule', () => {
      const req = createRequest('/store/5/orders', true, { 'platform-role': 'cast' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/cast/schedule' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a platform-console session on /cast/* to /platform/dashboard', () => {
      const req = createRequest('/cast/schedule', true, { 'platform-role': 'platform' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/platform/dashboard' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a member-console session on /platform/* to /member', () => {
      const req = createRequest('/platform/dashboard', true, { 'platform-role': 'member' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/member' })
      );
      expect(res).not.toBeNull();
    });

    it('redirects a member-console session on /store/* to /member', () => {
      const req = createRequest('/store/5/orders', true, { 'platform-role': 'member' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/member' })
      );
      expect(res).not.toBeNull();
    });

    it('allows a member-console session on /member', () => {
      const req = createRequest('/member', true, { 'platform-role': 'member' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('redirects a cast-console session on /member/* to /cast/schedule', () => {
      const req = createRequest('/member', true, { 'platform-role': 'cast' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/cast/schedule' })
      );
      expect(res).not.toBeNull();
    });

    it('allows a platform-console session on /platform/*', () => {
      const req = createRequest('/platform/dashboard', true, { 'platform-role': 'platform' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('allows a platform-console session on an id-scoped /store/* route (store bridge)', () => {
      const req = createRequest('/store/5/orders', true, { 'platform-role': 'platform' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('allows a store-console session on an id-scoped /store/* route', () => {
      const req = createRequest('/store/5/orders', true, { 'platform-role': 'store' });
      const res = handleRouteProtection(req, 'store');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('allows a store-console session on the public /platform/login route', () => {
      const req = createRequest('/platform/login', true, { 'platform-role': 'store' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('does not guard when the console cookie is absent (legacy session)', () => {
      const req = createRequest('/platform/dashboard', true);
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('does not guard an unknown (old-format) console cookie value', () => {
      const req = createRequest('/platform/dashboard', true, { 'platform-role': 'admin' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('does not crash or guard on a prototype-chain cookie value', () => {
      const req = createRequest('/platform/dashboard', true, { 'platform-role': 'constructor' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('allows a store-console session on the shared /platform/settings/account route', () => {
      const req = createRequest('/platform/settings/account', true, { 'platform-role': 'store' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('allows a cast-console session on the shared /platform/settings/account route', () => {
      const req = createRequest('/platform/settings/account', true, { 'platform-role': 'cast' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).not.toHaveBeenCalled();
      expect(res).toBeNull();
    });

    it('still guards a store-console session on the non-shared /platform/settings hub', () => {
      const req = createRequest('/platform/settings', true, { 'platform-role': 'store' });
      const res = handleRouteProtection(req, 'platform');

      expect(NextResponse.redirect).toHaveBeenCalledWith(
        expect.objectContaining({ pathname: '/store/entry' })
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
