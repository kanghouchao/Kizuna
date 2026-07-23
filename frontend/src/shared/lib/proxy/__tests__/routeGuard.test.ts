import { NextRequest, NextResponse } from 'next/server';
import { handleRouteProtection } from '../routeGuard';

// Mock NextResponse.redirect
jest.mock('next/server', () => {
  return {
    NextResponse: {
      redirect: jest.fn(url => ({
        status: 307,
        headers: { get: () => url.toString() },
      })),
    },
  };
});

const createRequest = (path: string, hasToken: boolean) => {
  return {
    nextUrl: {
      pathname: path,
    },
    url: 'http://localhost' + path,
    cookies: {
      has: (name: string) => (name === 'token' ? hasToken : false),
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

  it('allows access to /platform/invite/:token without token (public route)', () => {
    const req = createRequest('/platform/invite/abc123', false);
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

  it('allows access to /store/select with token (no legacy redirect)', () => {
    const req = createRequest('/store/select', true);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).not.toHaveBeenCalled();
    expect(res).toBeNull();
  });

  it('redirects a legacy id-less /store path (with token) to /store/select preserving next', () => {
    const req = createRequest('/store/orders', true);
    const res = handleRouteProtection(req, 'store');

    expect(NextResponse.redirect).toHaveBeenCalledWith(
      expect.objectContaining({
        pathname: '/store/select',
        search: '?next=%2Fstore%2Forders',
      })
    );
    expect(res).not.toBeNull();
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

  it('ignores other routes', () => {
    const req = createRequest('/public/page', false);
    const res = handleRouteProtection(req, 'store'); // Role doesn't strictly matter for non-protected routes in current implementation

    expect(res).toBeNull();
  });
});
