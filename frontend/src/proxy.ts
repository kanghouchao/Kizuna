import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { resolveTenant } from './lib/proxy/tenantResolver';
import { handleRouteProtection } from './lib/proxy/routeGuard';

export const config = {
  matcher: ['/((?!api|_next|favicon.ico|health).*)'],
};

export async function proxy(request: NextRequest) {
  // 1. Identify Role & Tenant
  const { role, tenantData } = await resolveTenant(request);

  // 2. Route Protection (Security Guard)
  const redirectResponse = handleRouteProtection(request, role);
  if (redirectResponse) {
    return redirectResponse;
  }

  // 3. Prepare Response & Cookies
  const response = NextResponse.next();
  const isHttps = request.nextUrl.protocol === 'https:';
  const cookieOptions = {
    httpOnly: false,
    sameSite: 'lax' as const,
    secure: isHttps,
    path: '/',
  };

  // Set Role Cookie
  response.cookies.set('x-mw-role', role, cookieOptions);

  // Set Tenant Cookies (if applicable)
  if (role === 'tenant' && tenantData?.isValid) {
    // ドメインを保存して、後続リクエストで検証に使用
    const hostname = (
      request.headers.get('x-forwarded-host') ||
      request.headers.get('host') ||
      request.nextUrl.hostname
    )
      .split(',')[0]
      .trim()
      .split(':')[0]
      .toLowerCase();
    response.cookies.set('x-mw-tenant-domain', hostname, cookieOptions);
    response.cookies.set('x-mw-tenant-template', tenantData.templateKey, cookieOptions);
    if (tenantData.tenantId) {
      response.cookies.set('x-mw-tenant-id', tenantData.tenantId, cookieOptions);
    }
    if (tenantData.tenantName) {
      response.cookies.set('x-mw-tenant-name', tenantData.tenantName, cookieOptions);
    }
  }

  return response;
}
