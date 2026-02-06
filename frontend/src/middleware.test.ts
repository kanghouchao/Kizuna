// Mock dependencies BEFORE importing middleware
jest.mock('./lib/middleware/tenantResolver', () => ({
  resolveTenant: jest.fn(),
}));

jest.mock('./lib/middleware/routeGuard', () => ({
  handleRouteProtection: jest.fn(),
}));

jest.mock('next/server', () => {
  class MockCookies {
    private store = new Map<string, string>();
    set(name: string, value: string) {
      this.store.set(name, value);
    }
    get(name: string) {
      return { value: this.store.get(name) };
    }
  }

  class MockNextResponse {
    public cookies = new MockCookies();
    public status = 200;
    static next() {
      return new MockNextResponse();
    }
  }

  return {
    NextResponse: MockNextResponse,
    NextRequest: class {},
  };
});

import { middleware } from './middleware';
import { resolveTenant } from './lib/middleware/tenantResolver';
import { handleRouteProtection } from './lib/middleware/routeGuard';
import { NextRequest } from 'next/server';

describe('middleware integration', () => {
  const mockResolveTenant = resolveTenant as jest.Mock;
  const mockHandleRouteProtection = handleRouteProtection as jest.Mock;

  const createRequest = (hostname = 'store.test') =>
    ({
      nextUrl: { protocol: 'http:', hostname },
      headers: {
        get: (name: string) => (name === 'host' ? hostname : null),
      },
    }) as unknown as NextRequest;

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('delegates to routeGuard and returns redirect if needed', async () => {
    mockResolveTenant.mockResolvedValue({ role: 'central' });
    const redirectResponse = { status: 307 };
    mockHandleRouteProtection.mockReturnValue(redirectResponse);

    const req = createRequest();
    const res = await middleware(req);

    expect(mockResolveTenant).toHaveBeenCalledWith(req);
    expect(mockHandleRouteProtection).toHaveBeenCalledWith(req, 'central');
    expect(res).toBe(redirectResponse);
  });

  it('sets cookies and proceeds if routeGuard allows', async () => {
    mockResolveTenant.mockResolvedValue({
      role: 'tenant',
      tenantData: {
        isValid: true,
        templateKey: 'dark',
        tenantId: 't1',
        tenantName: 'Shop',
      },
    });
    mockHandleRouteProtection.mockReturnValue(null);

    const req = createRequest();
    const res = (await middleware(req)) as any;

    expect(res.status).toBe(200);
    expect(res.cookies.get('x-mw-role').value).toBe('tenant');
    expect(res.cookies.get('x-mw-tenant-id').value).toBe('t1');
    expect(res.cookies.get('x-mw-tenant-template').value).toBe('dark');
    expect(res.cookies.get('x-mw-tenant-domain').value).toBe('store.test');
  });
});
