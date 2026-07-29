import { NextRequest, NextResponse } from 'next/server';
import { isLegacyStorePath, isRetiredStorePath, storeEntryPath } from '../store-route';
import { consoleAreaOfPath, isPublicPlatformPath, isSharedPlatformPath } from '../app-area';

// コンソール別の入場可能エリアとホーム。/me の console（サーバ側が能力目録から導出）を
// ログイン時に保存した platform-role cookie が根拠。platform コンソールは storeBridge に
// よる店舗コンソール操作があるため /store も許可する。認可の根拠ではなく画面遷移の整合のみ
// — データはバックエンドが能力ベースで fail-closed に拒否する。
const CONSOLE_AREAS: Record<string, { allowed: readonly string[]; home: string }> = {
  platform: { allowed: ['platform', 'store'], home: '/platform/dashboard' },
  store: { allowed: ['store'], home: storeEntryPath() },
  cast: { allowed: ['cast'], home: '/cast/schedule' },
};

export function handleRouteProtection(request: NextRequest, role: 'platform' | 'store') {
  const path = request.nextUrl.pathname;
  const hasToken = request.cookies.has('token');

  // 公開 platform ルート（ログインフォーム・招待受諾 — セッション不要）は守衛の対象外。
  // これを除外しないと /platform/login への redirect 自身が /platform 前綴に再マッチし、
  // 無限リダイレクト（ERR_TOO_MANY_REDIRECTS）に陥る。
  const isPublicPlatformRoute = isPublicPlatformPath(path);

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
  // 入口のため、未トークンはそちらへ差し戻す（/store の既定遷移先である「/」とは別系統）。
  // 公開ストアフロントの /casts・/casts/:id を巻き込まないため厳密一致＋/cast/ 配下に限定する。
  if ((path === '/cast' || path.startsWith('/cast/')) && !hasToken) {
    console.error('🔒 Unauthorized access to /cast, redirecting to login');
    return NextResponse.redirect(new URL('/platform/login', request.url));
  }

  // 3. Console/area alignment
  // 店舗文脈のまま /platform/* を直打ちすると、サイドバー（能力由来）と本文（URL 由来）が
  // 食い違ったまま描画され、平台 API も 403 になる。トークン保持者のコンソールと URL エリアが
  // 一致しない場合は自コンソールのホームへ差し戻す。cookie 不在（レガシーセッション）や
  // 未知の旧形式値は対象外 — 後者は apiClient の 403 応答経路がセッション破棄で回収する。
  // 共有 platform ルート（自アカウント設定）は店舗コンソールの正当な到達先のため対象外。
  if (hasToken && !isPublicPlatformRoute && !isSharedPlatformPath(path)) {
    const consoleValue = request.cookies.get('platform-role')?.value ?? '';
    // cookie は利用者が任意値を書ける。素の添字だと 'constructor' 等が原型鎖に当たるため
    // 自有プロパティに限定する。
    const consoleArea = Object.hasOwn(CONSOLE_AREAS, consoleValue)
      ? CONSOLE_AREAS[consoleValue]
      : undefined;
    const pathArea = consoleAreaOfPath(path);
    if (consoleArea && pathArea && !consoleArea.allowed.includes(pathArea)) {
      return NextResponse.redirect(new URL(consoleArea.home, request.url));
    }
  }

  // 4. Legacy id-less / retired store URL handling
  // id 無しの店舗 URL（例 /store/orders、ブックマーク・共有リンクに残りうる）は
  // /store/[storeId]/... にも /store/entry にもマッチせず 404 になる。
  // 廃止済みルート（/store/dashboard・/store/5/dashboard 等）も同じく遷移先が無い。
  // トークン保持者に限り店舗コンソールの入口へ誘導し、解決後に元の遷移先（next）へ復帰させる
  // — 廃止済みの next は入口が捨て、メニュー由来の着地先へ回す（捨てる判断は入口に一元化）。
  // 数値id配下（/store/5/...）と /store/entry 自体はレガシー判定の対象外。トークン無しは上の
  // 分岐で処理済みのため hasToken を明示条件にする（トークン無しはルートへ戻す既存挙動を維持する）。
  // 判定の正規表現は store-route（店舗パス知識の唯一 module）へ集約済み。
  if (hasToken && (isLegacyStorePath(path) || isRetiredStorePath(path))) {
    return NextResponse.redirect(new URL(storeEntryPath(path), request.url));
  }

  return null; // No redirection needed
}
