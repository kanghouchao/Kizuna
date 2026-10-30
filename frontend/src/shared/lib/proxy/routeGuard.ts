import { NextRequest, NextResponse } from 'next/server';
import { consoleAreaOfPath, isPublicAuthPath, isSharedAdminPath } from '../app-area';
import { MEMBER_RETURN_PATH_COOKIE, isSafeMemberReturnPath } from '../session';
import type { HostRole } from './storeResolver';

// セッション種別（ログイン時に保存した session-kind cookie）ごとの入場可能エリアとホーム。
// 従業員の管理画面 admin は /admin と /store の両方を一つの画面として持つ。認可の根拠ではなく
// 画面遷移の整合のみ — データはバックエンドが権限ベースで fail-closed に拒否する。
const CONSOLE_AREAS: Record<string, { allowed: readonly string[]; home: string }> = {
  admin: { allowed: ['admin', 'store'], home: '/admin/dashboard' },
  cast: { allowed: ['cast'], home: '/cast/schedule' },
  member: { allowed: ['member'], home: '/member' },
};

/**
 * 伝票トークンの申領画面ちょうど（末尾スラッシュの有無は問わない）。
 *
 * <p>この画面だけはサーバ側で差し戻さない。伝票トークンは QR の URL のフラグメントで届き、
 * フラグメントはサーバへ送られない — 描かずに差し戻すとトークンは読み取られないまま消える。
 * 未認証も別コンソールのセッションも同じ理由で素通しし、画面自身がトークンを預けてログインへ送る。
 */
function isMemberReceiptClaimPath(path: string): boolean {
  return path === '/member/receipts' || path === '/member/receipts/';
}

export function handleRouteProtection(request: NextRequest, _role: HostRole) {
  const path = request.nextUrl.pathname;
  const hasToken = request.cookies.has('token');

  // 公開 auth ルート（ログインフォーム・招待受諾・会員登録・LINE コールバック・初期設定）はセッション不要で、
  // 未認証の差し戻しにもエリア整合の守衛にも掛けない。
  const isPublicAuthRoute = isPublicAuthPath(path);

  if (path.startsWith('/admin') && !isPublicAuthRoute && !hasToken) {
    console.error('🔒 Unauthorized access to /admin, redirecting to login');
    return NextResponse.redirect(new URL('/auth/login', request.url));
  }

  if (path.startsWith('/store') && !hasToken) {
    console.error('🔒 Unauthorized access to /store, redirecting to root');
    return NextResponse.redirect(new URL('/', request.url));
  }

  // /cast/** はキャストポータル専用の認証済み領域。専用ログイン画面は無く /auth/login が
  // 入口のため、未トークンはそちらへ差し戻す（/store の既定遷移先である「/」とは別系統）。
  // 公開ストアフロントの /casts・/casts/:id を巻き込まないため厳密一致＋/cast/ 配下に限定する。
  if ((path === '/cast' || path.startsWith('/cast/')) && !hasToken) {
    console.error('🔒 Unauthorized access to /cast, redirecting to login');
    return NextResponse.redirect(new URL('/auth/login', request.url));
  }

  // /member/** は会員ポータル専用の認証済み領域。入口は /auth/login（キャストと同様、
  // 専用ログイン画面は無い）。厳密一致＋/member/ 配下に限定するのも同じ理由。
  //
  // 申領画面だけは未認証でも描かせる（理由は isMemberReceiptClaimPath）。免除は正確に
  // このパスだけで、前綴では緩めない。
  if (
    !isMemberReceiptClaimPath(path) &&
    (path === '/member' || path.startsWith('/member/')) &&
    !hasToken
  ) {
    console.error('🔒 Unauthorized access to /member, redirecting to login');
    const response = NextResponse.redirect(new URL('/auth/login', request.url));
    // ここで差し戻す時点では会員ポータルの画面は描画されておらず、クライアント側では戻り先を
    // 覚えられない。ログイン後に元の画面（店舗つき予約導線など）へ戻すため、リダイレクト応答の
    // cookie に残す。白名単（/member/ 配下の相対パスのみ）は保存時にも通し、開放リダイレクトを防ぐ。
    const returnPath = `${path}${request.nextUrl.search}`;
    if (isSafeMemberReturnPath(returnPath)) {
      response.cookies.set(MEMBER_RETURN_PATH_COOKIE, returnPath, { path: '/' });
    }
    return response;
  }

  // キャスト・会員のセッションで管理画面を直打ちすると、サイドバーと本文が食い違ったまま描画され
  // API も 403 になる。セッション種別と URL エリアが一致しない場合は自分のホームへ差し戻す。
  // cookie 不在（レガシーセッション）や未知の旧形式値は対象外 — 後者は apiClient の 403 応答経路が
  // セッション破棄で回収する。共有 admin ルート（自アカウント設定）は種別に依らない到達先のため対象外。
  // 申領画面も対象外 — 別コンソールのセッションが残ったまま QR を開く場面でここが働くと、
  // 描かれる前に差し戻されてフラグメントのトークンが消える（未認証の免除と同じ理由）。
  if (
    hasToken &&
    !isPublicAuthRoute &&
    !isSharedAdminPath(path) &&
    !isMemberReceiptClaimPath(path)
  ) {
    const consoleValue = request.cookies.get('session-kind')?.value ?? '';
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

  return null;
}
