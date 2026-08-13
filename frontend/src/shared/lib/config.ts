import Cookies from 'js-cookie';

/**
 * 平台ドメイン。店舗ドメインから会員ポータルへ渡す導線が絶対 URL を組むために使う。
 *
 * 根拠は proxy が実行時 env から立てる cookie。`NEXT_PUBLIC_*` はビルド時のインライン置換で
 * クライアントへ届く仕組みで、この値をビルドへ渡す経路が無いため、実行時 environment に
 * 積んでもブラウザ側は既定値へ落ちる（＝本番で test ドメインを指す）。
 */
export function getPlatformDomain(): string {
  return Cookies.get('x-mw-platform-domain') || 'kizuna.test';
}

/**
 * 店舗別ドメインで配信されているか。proxy が Host から解決した role が根拠。
 *
 * ドメイン文字列の比較にはしない — 突き合わせ相手の平台ドメインが取れなければ全ホストが
 * 店舗ドメイン扱いになり、平台ドメイン上でも判定が裏返るため。
 */
export function isStoreDomain(): boolean {
  return Cookies.get('x-mw-role') === 'store';
}
