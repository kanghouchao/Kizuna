import type { NextRequest } from 'next/server';
import { proxy } from './proxy';
import { handleRouteProtection } from './shared/lib/proxy/routeGuard';

jest.mock('./shared/lib/proxy/routeGuard', () => ({
  handleRouteProtection: jest.fn(),
}));

const passThrough = { kind: 'next' };
jest.mock('next/server', () => ({
  NextResponse: { next: () => passThrough },
}));

const mockHandleRouteProtection = handleRouteProtection as jest.Mock;

describe('proxy', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('守衛が差し戻すときはそのリダイレクトを返す', () => {
    const redirect = { status: 307 };
    mockHandleRouteProtection.mockReturnValue(redirect);
    const request = {} as NextRequest;

    expect(proxy(request)).toBe(redirect);
    expect(mockHandleRouteProtection).toHaveBeenCalledWith(request);
  });

  it('守衛を通過したリクエストはそのまま進める', () => {
    mockHandleRouteProtection.mockReturnValue(null);

    expect(proxy({} as NextRequest)).toBe(passThrough);
  });
});
