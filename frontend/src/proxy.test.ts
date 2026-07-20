import { proxy } from './proxy';
import { resolveStore } from './shared/lib/proxy/storeResolver';
import { handleRouteProtection } from './shared/lib/proxy/routeGuard';
import { NextRequest } from 'next/server';

// Mock dependencies BEFORE importing proxy
jest.mock('./shared/lib/proxy/storeResolver', () => ({
  resolveStore: jest.fn(),
}));

jest.mock('./shared/lib/proxy/routeGuard', () => ({
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

describe('proxy integration', () => {
  const mockResolveStore = resolveStore as jest.Mock;
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
    mockResolveStore.mockResolvedValue({ role: 'platform' });
    const redirectResponse = { status: 307 };
    mockHandleRouteProtection.mockReturnValue(redirectResponse);

    const req = createRequest();
    const res = await proxy(req);

    expect(mockResolveStore).toHaveBeenCalledWith(req);
    expect(mockHandleRouteProtection).toHaveBeenCalledWith(req, 'platform');
    expect(res).toBe(redirectResponse);
  });

  it('sets cookies and proceeds if routeGuard allows', async () => {
    mockResolveStore.mockResolvedValue({
      role: 'store',
      storeData: {
        isValid: true,
        templateKey: 'dark',
        storeId: 't1',
        storeName: 'Shop',
      },
    });
    mockHandleRouteProtection.mockReturnValue(null);

    const req = createRequest();
    const res = (await proxy(req)) as any;

    expect(res.status).toBe(200);
    expect(res.cookies.get('x-mw-role').value).toBe('store');
    expect(res.cookies.get('x-mw-store-id').value).toBe('t1');
    expect(res.cookies.get('x-mw-store-template').value).toBe('dark');
    expect(res.cookies.get('x-mw-store-domain').value).toBe('store.test');
  });
});
