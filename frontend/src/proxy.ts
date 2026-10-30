import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { handleRouteProtection } from './shared/lib/proxy/routeGuard';

// 画面遷移の守衛が要るエリアだけを通す。公開サイトと認証画面の応答は proxy を通らない。
export const config = {
  matcher: ['/admin/:path*', '/store/:path*', '/cast/:path*', '/member/:path*'],
};

export function proxy(request: NextRequest) {
  return handleRouteProtection(request) ?? NextResponse.next();
}
