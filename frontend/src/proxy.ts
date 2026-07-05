import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { resolveTenant } from './shared/lib/proxy/tenantResolver';
import { handleRouteProtection } from './shared/lib/proxy/routeGuard';

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
    // template cookie のみ maxAge 60 秒（ISR revalidate 60 秒と揃える）。
    // session cookie のままだと一度立った模版が既存訪問者に永久固定され、
    // 模版変更が伝播しない。短命化して最大 ~2 分で全訪問者へ反映させる。
    response.cookies.set('x-mw-tenant-template', tenantData.templateKey, {
      ...cookieOptions,
      maxAge: 60,
    });
    if (tenantData.tenantId) {
      response.cookies.set('x-mw-tenant-id', tenantData.tenantId, cookieOptions);
    }
    if (tenantData.tenantName) {
      response.cookies.set('x-mw-tenant-name', tenantData.tenantName, cookieOptions);
    }
  }

  return response;
}
