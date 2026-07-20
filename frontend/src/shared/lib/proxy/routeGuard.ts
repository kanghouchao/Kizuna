import { NextRequest, NextResponse } from 'next/server';

export function handleRouteProtection(request: NextRequest, role: 'platform' | 'store') {
  const path = request.nextUrl.pathname;
  const hasToken = request.cookies.has('token');

  // 1. Platform Route Protection
  // If accessing /platform/* without a token, redirect to /platform/login
  if (path.startsWith('/platform') && !hasToken) {
    console.error('🔒 Unauthorized access to /platform, redirecting to login');
    return NextResponse.redirect(new URL('/platform/login', request.url));
  }

  // 2. Store Route Protection
  // If accessing /store/* without a token, redirect to root (/)
  // This logic applies even if role is 'platform' but accessing store routes (though rare)
  // But strictly, we mostly care about store role here.
  if (path.startsWith('/store') && !hasToken) {
    console.error('🔒 Unauthorized access to /store, redirecting to root');
    return NextResponse.redirect(new URL('/', request.url));
  }

  return null; // No redirection needed
}
