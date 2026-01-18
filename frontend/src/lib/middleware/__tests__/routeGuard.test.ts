import { NextRequest, NextResponse } from 'next/server';
import { handleRouteProtection } from '../routeGuard';

// Mock NextResponse.redirect
jest.mock('next/server', () => {
  return {
    NextResponse: {
      redirect: jest.fn((url) => ({
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

  it('redirects to /login when accessing /central without token', () => {
    const req = createRequest('/central/dashboard', false);
    const res = handleRouteProtection(req, 'central');

    expect(NextResponse.redirect).toHaveBeenCalledWith(expect.objectContaining({ pathname: '/login' }));
    expect(res).not.toBeNull();
  });

  it('allows access to /central with token', () => {
    const req = createRequest('/central/dashboard', true);
    const res = handleRouteProtection(req, 'central');

    expect(res).toBeNull();
  });

  it('redirects to / when accessing /tenant without token', () => {
    const req = createRequest('/tenant/orders', false);
    const res = handleRouteProtection(req, 'tenant');

    expect(NextResponse.redirect).toHaveBeenCalledWith(expect.objectContaining({ pathname: '/' }));
    expect(res).not.toBeNull();
  });

  it('allows access to /tenant with token', () => {
    const req = createRequest('/tenant/orders', true);
    const res = handleRouteProtection(req, 'tenant');

    expect(res).toBeNull();
  });

  it('ignores other routes', () => {
    const req = createRequest('/public/page', false);
    const res = handleRouteProtection(req, 'tenant'); // Role doesn't strictly matter for non-protected routes in current implementation

    expect(res).toBeNull();
  });
});
