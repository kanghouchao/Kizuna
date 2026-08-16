import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { PLATFORM_DOMAIN, resolveStore } from './shared/lib/proxy/storeResolver';
import { handleRouteProtection } from './shared/lib/proxy/routeGuard';

export const config = {
  matcher: ['/((?!api|_next|favicon.ico|health).*)'],
};

export async function proxy(request: NextRequest) {
  const { role, storeData } = await resolveStore(request);

  const redirectResponse = handleRouteProtection(request, role);
  if (redirectResponse) {
    return redirectResponse;
  }

  const response = NextResponse.next();
  const isHttps = request.nextUrl.protocol === 'https:';
  const cookieOptions = {
    httpOnly: false,
    sameSite: 'lax' as const,
    secure: isHttps,
    path: '/',
  };

  response.cookies.set('x-mw-role', role, cookieOptions);

  // 平台ドメインをクライアントへ渡す。店舗ドメインから会員ポータルへ渡す導線は絶対 URL を
  // 組む必要があるが、NEXT_PUBLIC_* はビルド時インライン置換のため実行時 env が届かない。
  // 実行時に決まる値は proxy が cookie で運ぶ（role と同じ流儀）。
  response.cookies.set('x-mw-platform-domain', PLATFORM_DOMAIN, cookieOptions);

  if (role === 'store' && storeData?.isValid) {
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
    response.cookies.set('x-mw-store-domain', hostname, cookieOptions);
    // template cookie のみ maxAge 60 秒（ISR revalidate 60 秒と揃える）。
    // session cookie のままだと一度立った模版が既存訪問者に永久固定され、
    // 模版変更が伝播しない。短命化して最大 ~2 分で全訪問者へ反映させる。
    response.cookies.set('x-mw-store-template', storeData.templateKey, {
      ...cookieOptions,
      maxAge: 60,
    });
    if (storeData.storeId) {
      response.cookies.set('x-mw-store-id', storeData.storeId, cookieOptions);
    }
    if (storeData.storeName) {
      response.cookies.set('x-mw-store-name', storeData.storeName, cookieOptions);
    }
  }

  return response;
}
