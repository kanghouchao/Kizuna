import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { resolveTenant } from './lib/middleware/tenantResolver';
import { handleRouteProtection } from './lib/middleware/routeGuard';

export const config = {
  matcher: ['/((?!api|_next|favicon.ico|health).*)'],
};

export async function middleware(request: NextRequest) {
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
