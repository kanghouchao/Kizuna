import { NextRequest, NextResponse } from 'next/server';

export function handleRouteProtection(request: NextRequest, role: 'central' | 'tenant') {
  const path = request.nextUrl.pathname;
  const hasToken = request.cookies.has('token');

  // 1. Central Route Protection
  // If accessing /central/* without a token, redirect to /login
  if (path.startsWith('/central') && !hasToken) {
    console.error('🔒 Unauthorized access to /central, redirecting to login');
    return NextResponse.redirect(new URL('/login', request.url));
  }

  // 2. Tenant Route Protection
  // If accessing /tenant/* without a token, redirect to root (/)
  // This logic applies even if role is 'central' but accessing tenant routes (though rare)
  // But strictly, we mostly care about tenant role here.
  if (path.startsWith('/tenant') && !hasToken) {
    console.error('🔒 Unauthorized access to /tenant, redirecting to root');
    return NextResponse.redirect(new URL('/', request.url));
  }

  return null; // No redirection needed
}
