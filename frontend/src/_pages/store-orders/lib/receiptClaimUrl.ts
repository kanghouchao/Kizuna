import { getPlatformDomain } from '@/shared/lib';

/**
 * 伝票トークンの申領 URL（QR が運ぶ値）。
 *
 * 会員ポータルは平台ドメイン側にあるので、店舗コンソールが配信されているドメインではなく
 * 平台ドメインへ組み立てる（店舗サイトから会員ポータルへ渡す導線と同じ流儀）。
 *
 * トークンはパスに置く。問い合わせ文字列に載せると Referer や履歴・アクセスログへ素通りするため、
 * 所持そのものが証明になる値は経路に残さない。末尾のスラッシュは付けない — 付けると 308 で
 * 落とされた URL を客の手元に配ることになる。
 */
export function receiptClaimUrl(token: string): string {
  const { protocol } = window.location;
  return `${protocol}//${getPlatformDomain()}/member/receipts/${encodeURIComponent(token)}`;
}
