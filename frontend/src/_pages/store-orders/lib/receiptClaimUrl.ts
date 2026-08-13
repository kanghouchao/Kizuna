import { getPlatformDomain } from '@/shared/lib';

/**
 * 伝票トークンの申領 URL（QR が運ぶ値）。
 *
 * 会員ポータルは平台ドメイン側にあるので、店舗コンソールが配信されているドメインではなく
 * 平台ドメインへ組み立てる（店舗サイトから会員ポータルへ渡す導線と同じ流儀）。
 *
 * トークンはフラグメントに置く。パスも問い合わせ文字列もリクエストターゲットとして送られ、
 * リバースプロキシとアプリのアクセスログに 90 日有効の生値がそのまま残る — 読み取られるたびに
 * ログへクレデンシャルを書くことになる。フラグメントはサーバへ送られないので、申領画面が
 * ブラウザ側で読み取って申領要求の本体に載せる形にできる。
 *
 * 末尾のスラッシュは付けない — 付けると 308 で落とされた URL を客の手元に配ることになる。
 */
export function receiptClaimUrl(token: string): string {
  const { protocol } = window.location;
  return `${protocol}//${getPlatformDomain()}/member/receipts#${encodeURIComponent(token)}`;
}
