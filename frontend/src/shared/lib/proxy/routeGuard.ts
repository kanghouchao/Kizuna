import { NextRequest, NextResponse } from 'next/server';
import { isLegacyStorePath, storeSelectPath } from '../store-route';

export function handleRouteProtection(request: NextRequest, role: 'platform' | 'store') {
  const path = request.nextUrl.pathname;
  const hasToken = request.cookies.has('token');

  // 公開 platform ルート（ログインフォーム・招待受諾 — セッション不要）は守衛の対象外。
  // これを除外しないと /platform/login への redirect 自身が /platform 前綴に再マッチし、
  // 無限リダイレクト（ERR_TOO_MANY_REDIRECTS）に陥る。
  const isPublicPlatformRoute =
    path.startsWith('/platform/login') || path.startsWith('/platform/invite');

  // 1. Platform Route Protection
  // If accessing a protected /platform/* route without a token, redirect to /platform/login
  if (path.startsWith('/platform') && !isPublicPlatformRoute && !hasToken) {
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

  // 2.5. Cast Portal Route Protection
  // /cast/** はキャストポータル専用の認証済み領域。専用ログイン画面は無く /platform/login が
  // 入口のため、未トークンは /platform（他の保護 prefix と別系統）へ差し戻す。
  if (path.startsWith('/cast') && !hasToken) {
    console.error('🔒 Unauthorized access to /cast, redirecting to login');
    return NextResponse.redirect(new URL('/platform/login', request.url));
  }

  // 3. Legacy id-less store URL handling
  // id 無しの店舗 URL（例 /store/orders、ブックマーク・共有リンクに残りうる）は
  // /store/[storeId]/... にも /store/select にもマッチせず 404 になる。
  // トークン保持者に限り店舗選択画面へ誘導し、選択後に元の遷移先（next）へ復帰させる。
  // 数値id配下（/store/5/...）と /store/select 自体は対象外。トークン無しは上の分岐で処理済みのため
  // hasToken を明示条件にする（トークン無しはルートへ戻す既存挙動を維持する）。
  // レガシー判定の正規表現は store-route（店舗パス知識の唯一 module）へ集約済み。
  if (hasToken && isLegacyStorePath(path)) {
    return NextResponse.redirect(new URL(storeSelectPath(path), request.url));
  }

  return null; // No redirection needed
}
